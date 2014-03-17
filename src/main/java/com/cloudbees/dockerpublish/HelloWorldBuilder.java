package com.cloudbees.dockerpublish;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link HelloWorldBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields )
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */

public class HelloWorldBuilder extends Builder {
    private final String repoName;
    private final boolean noCache;
    private String repoTag;
    private boolean skipPush = true;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public HelloWorldBuilder(String repoName, String repoTag, boolean skipPush, boolean noCache, String workspaceDir) {
        this.repoName = repoName;
        this.repoTag = repoTag;
        this.skipPush = skipPush;
        this.noCache = noCache;
    }

    public String getRepoName() {return repoName; }
    public String getRepoTag() {  return repoTag; }
    public boolean isSkipPush() { return skipPush;}
    public boolean isNoCache() { return noCache;}




    /**
     * this tag is what is used to build - but not to push to the registry.
     * In docker - you push the whole repo to trigger the sync.
     */
    private String getNameAndTag() {
        if (getRepoTag() == null || repoTag.trim().isEmpty()) {
            return repoName;
        } else {
            return repoName + ":" + repoTag;
        }
    }


    private ArgumentListBuilder dockerLoginCommand() {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("docker").add("login").add("-u").add(getDescriptor().getUserName()).add("-e").add(getDescriptor().getEmail()).add("-p").addMasked(getDescriptor().getPassword());
        return args;
    }

    private String dockerBuildCommand(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException, MacroEvaluationException {
        String buildTag = TokenMacro.expandAll(build, listener, getNameAndTag());
        return "docker build -q -t " + buildTag + ((isNoCache()) ? " --no-cache=true " : "")  + " .";
    }

    private String dockerPushCommand(AbstractBuild build, BuildListener listener) throws InterruptedException, MacroEvaluationException, IOException {
        return "docker push " + TokenMacro.expandAll(build, listener, getRepoName());
    }


    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)  {
        try {
            build.setDisplayName(build.getDisplayName() + " " + TokenMacro.expandAll(build, listener, getNameAndTag()));

            executeCmd(build, launcher, listener, dockerLoginCommand());
            executeCmd(build, launcher, listener, dockerBuildCommand(build, listener));

            if (!isSkipPush()) {
                executeCmd(build, launcher, listener, dockerPushCommand(build, listener));
            }

        } catch (IOException e) {
            recordException(listener, e);
            return false;
        } catch (InterruptedException e) {
            recordException(listener, e);
            return false;
        } catch (MacroEvaluationException e) {
            recordException(listener, e);
            return false;
        }
        //listener.getLogger().println("Hello67, " + name + "!");

        return true;
    }

    private int executeCmd(AbstractBuild build, Launcher launcher, BuildListener listener, ArgumentListBuilder args) throws IOException, InterruptedException {
        return launcher.launch()
            .envs(build.getEnvironment(listener))
            .pwd(build.getWorkspace())
            .stdout(listener.getLogger())
            .stderr(listener.getLogger())
            .cmds(args)
            .start().join();
    }


    private int executeCmd(AbstractBuild build, Launcher launcher, BuildListener listener, String cmd) throws IOException, InterruptedException {
        return launcher.launch()
                .envs(build.getEnvironment(listener))
                .pwd(build.getWorkspace())
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .cmdAsSingleString(cmd)
                .start().join();
    }


    private void recordException(BuildListener listener, Exception e) {
        listener.error(e.getMessage());
        e.printStackTrace(listener.getLogger());
    }


    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public String getUserName() {
            return userName;
        }

        public String getPassword() {
            return password;
        }

        public String getEmail() {
            return email;
        }

        private String userName;
        private String password;
        private String email;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckRepoName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Docker build and publish";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            userName = formData.getString("userName");
            password = formData.getString("password");
            email = formData.getString("email");

            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }


    }
}

