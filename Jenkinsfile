def appName = "dora-sync"
def imageRepository = ""
def imageTag = ""
def latestImageTag = ""
def imageVersion = ""

pipeline {
    agent any

    stages {
        stage('Prepare') {
            steps {
                script {
                    imageVersion = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'latest'}"
                    imageRepository = "${env.REGISTRY_URL}/${appName}"
                    imageTag = "${imageRepository}:${imageVersion}"
                    latestImageTag = "${imageRepository}:latest"
                    echo "Docker image tag: ${imageTag}"
                    echo "Docker latest tag: ${latestImageTag}"
                }
            }
        }
        stage('Test') {
            steps {
                sh 'mvn test --no-transfer-progress'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }
        stage('Dockerize') {
            steps {
                sh """
                    mvn spring-boot:build-image \
                      -DskipTests \
                      -Dspring-boot.build-image.imageName="${imageTag}" \
                      -Dgit.commit.sha="${env.GIT_COMMIT}" \
                      -Dgit.repo.url="${env.GIT_URL}" \
                      --no-transfer-progress
                """
            }
        }
        stage('Push') {
            steps {
                sh "docker tag ${imageTag} ${latestImageTag}"
                sh "docker push ${imageTag}"
                sh "docker push ${latestImageTag}"
            }
            post {
                always {
                    sh "docker rmi ${imageTag} || true"
                    sh "docker rmi ${latestImageTag} || true"
                }
            }
        }
        stage('Publish Event') {
            steps {
                script {
                    rabbitMQPublisher(
                        rabbitName: 'rabbitmq',
                        exchange: 'ci-events',
                        routingKey: 'pipeline.completed',
                        data: groovy.json.JsonOutput.toJson([
                            event: 'pipeline_completed',
                            status: 'success',
                            imageRepository: imageRepository,
                            imageVersion: imageVersion,
                            buildNumber: env.BUILD_NUMBER as Integer
                        ]),
                        conversion: false,
                        toJson: false
                    )
                }
            }
        }
    }
    post {
        always {
            cleanWs()
        }
        success {
            echo "Build ${env.BUILD_NUMBER} of ${env.JOB_NAME} succeeded."
        }
        failure {
            echo "Build ${env.BUILD_NUMBER} of ${env.JOB_NAME} failed."
        }
        unstable {
            echo "Build ${env.BUILD_NUMBER} is unstable — some tests failed."
        }
    }
}
