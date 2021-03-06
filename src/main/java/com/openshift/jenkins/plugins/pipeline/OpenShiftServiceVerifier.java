package com.openshift.jenkins.plugins.pipeline;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IService;

import javax.servlet.ServletException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

public class OpenShiftServiceVerifier extends OpenShiftBaseStep {

	protected final static String DISPLAY_NAME = "Verify OpenShift Service";
	
    protected final String svcName;
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftServiceVerifier(String apiURL, String svcName, String namespace, String authToken, String verbose) {
    	super(apiURL, namespace, authToken, verbose);
        this.svcName = svcName;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getSvcName() {
		if (svcName == null)
			return "";
		return svcName;
	}
	
	public String getSvcName(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("svcName"))
			return overrides.get("svcName");
		return getSvcName();
	}

    public boolean coreLogic(Launcher launcher, TaskListener listener, EnvVars env, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_SERVICE_VERIFY, DISPLAY_NAME, getSvcName(overrides), getNamespace(overrides)));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	String spec = null;
    	
    	if (client != null) {
        	// get Service
        	IService svc = client.get(ResourceKind.SERVICE, getSvcName(overrides), getNamespace(overrides));
        	String ip = svc.getPortalIP();
        	int port = svc.getPort();
        	spec = ip + ":" + port;
        	int tryCount = 0;
        	if (chatty)
        		listener.getLogger().println("\nOpenShiftServiceVerifier retry " + getDescriptor().getRetry());
        	listener.getLogger().println(String.format(MessageConstants.SERVICE_CONNECTING, spec));
        	while (tryCount < getDescriptor().getRetry()) {
        		tryCount++;
        		if (chatty) listener.getLogger().println("\nOpenShiftServiceVerifier attempt connect to " + spec + " attempt " + tryCount);
        		InetSocketAddress address = new InetSocketAddress(ip,port);
        		Socket socket = null;
        		try {
        			socket = new Socket();
	        		socket.connect(address, 2500);
                	listener.getLogger().println(String.format(MessageConstants.EXIT_SERVICE_VERIFY_GOOD, DISPLAY_NAME, spec));
	        		return true;
				} catch (IOException e) {
					if (chatty) e.printStackTrace(listener.getLogger());
					try {
						Thread.sleep(2500);
					} catch (InterruptedException e1) {
					}
				} finally {
					try {
						socket.close();
					} catch (IOException e) {
						if (chatty)
							e.printStackTrace(listener.getLogger());
					}
				}
        	}
            	
        	
    	} else {
    		return false;
    	}

    	listener.getLogger().println(String.format(MessageConstants.EXIT_SERVICE_VERIFY_BAD, DISPLAY_NAME, spec));

    	return false;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftServiceVerifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private int retry = 100;
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the various fields.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckApiURL(value);
        }

        public FormValidation doCheckSvcName(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckSvcName(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckNamespace(value);
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return DISPLAY_NAME;
        }
        
        public int getRetry() {
        	return retry;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
        	retry = formData.getInt("retry");
            save();
            return super.configure(req,formData);
        }

    }

}

