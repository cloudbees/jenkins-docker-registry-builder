package com.cloudbees.dockerpublish;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;

import java.io.IOException;

/**
 * Created by michaelneale on 12/03/2014.
 */
public class DockerPush extends Publisher {


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return super.perform(build, launcher, listener);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return null;
    }
}
