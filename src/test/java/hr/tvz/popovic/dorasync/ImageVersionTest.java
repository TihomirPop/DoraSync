package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.ImageVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ImageVersionTest {

    @Test
    void parsesTheBuildNumberFromTheLeadingSegment() {
        assertEquals(
                new BuildNumber(12345),
                ((Maybe.Some<BuildNumber>) new ImageVersion("12345-ab12cd3").buildNumber()).value()
        );
    }

    @Test
    void parsesAnImageVersionThatIsOnlyABuildNumber() {
        assertEquals(
                new BuildNumber(7),
                ((Maybe.Some<BuildNumber>) new ImageVersion("7").buildNumber()).value()
        );
    }

    @Test
    void isNoneWhenTheLeadingSegmentIsNotNumeric() {
        assertInstanceOf(Maybe.None.class, new ImageVersion("v1.2.3-ab12cd3").buildNumber());
        assertInstanceOf(Maybe.None.class, new ImageVersion("latest").buildNumber());
        assertInstanceOf(Maybe.None.class, new ImageVersion("-ab12cd3").buildNumber());
    }
}
