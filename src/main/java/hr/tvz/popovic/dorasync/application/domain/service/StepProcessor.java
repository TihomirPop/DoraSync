package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.domain.model.StepResult;

public interface StepProcessor<S extends JobStep> {

    StepResult process(S step);
}
