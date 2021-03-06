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

import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.yaml.snakeyaml.Yaml;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.restclient.IClient;

import javax.servlet.ServletException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class OpenShiftCreator extends OpenShiftApiObjHandler {
	
	protected final static String DISPLAY_NAME = "Create OpenShift Resource(s)";
    protected final String jsonyaml;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftCreator(String apiURL, String namespace, String authToken, String verbose, String jsonyaml) {
    	super(apiURL, namespace, authToken, verbose);
    	this.jsonyaml = jsonyaml;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

    public String getJsonyaml() {
    	if (jsonyaml == null)
    		return "";
    	return jsonyaml;
    }
    
    public String getJsonyaml(Map<String,String> overrides) {
    	if (overrides != null && overrides.containsKey("jsonyaml"))
    		return overrides.get("jsonyaml");
    	return getJsonyaml();
    }
    
    protected boolean makeRESTCall(boolean chatty, TaskListener listener, String path, ModelNode resource, Map<String,String> overrides) {
		String response = null;
		URL url = null;
		if (apiMap.get(path) == null) {
			listener.getLogger().println(String.format(MessageConstants.TYPE_NOT_SUPPORTED, path));
			return false;
		}
		
    	try {
    		if (chatty) listener.getLogger().println("\nOpenShiftCreator POST URI " + apiMap.get(path)[0] + "/" + resource.get("apiVersion").asString() + "/namespaces/" +getNamespace(overrides) + "/" + apiMap.get(path)[1]);
			url = new URL(getApiURL(overrides) + apiMap.get(path)[0] + "/" + resource.get("apiVersion").asString() + "/namespaces/" + getNamespace(overrides) + "/" + apiMap.get(path)[1]);
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
			return false;
		}
    	
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	if (client == null) {
    		return false;
    	}
		try {
	    	KubernetesResource kr = new KubernetesResource(resource, client, null);
			response = createHttpClient().post(url, 10 * 1000, kr);
			if (chatty) listener.getLogger().println("\nOpenShiftCreator REST POST response " + response);
		} catch (SocketTimeoutException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
	    	listener.getLogger().println(String.format(MessageConstants.SOCKET_TIMEOUT, DISPLAY_NAME, getApiURL(overrides)));
			return false;
		} catch (HttpClientException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
	    	listener.getLogger().println(String.format(MessageConstants.HTTP_ERR, e1.getMessage(), DISPLAY_NAME, getApiURL(overrides)));
			return false;
		}
		
		return true;
    }
    
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_CREATE_OBJS, getNamespace(overrides)));
    	updateApiTypes(chatty, listener, overrides);
    	
    	ModelNode resources = this.hydrateJsonYaml(getJsonyaml(overrides), chatty ? listener : null);
    	if (resources == null) {
    		return false;
    	}
    	    	
    	//cycle through json and POST to appropriate resource
    	String kind = resources.get("kind").asString();
    	int created = 0;
    	int failed = 0;
    	if (kind.equalsIgnoreCase("List")) {
    		List<ModelNode> list = resources.get("items").asList();
    		for (ModelNode node : list) {
    			String path = node.get("kind").asString();
				
    			boolean success = this.makeRESTCall(chatty, listener, path, node, overrides);
    			if (!success) {
    				listener.getLogger().println(String.format(MessageConstants.FAILED_OBJ, path));
    				failed++;
    			} else {
    				listener.getLogger().println(String.format(MessageConstants.CREATED_OBJ, path));
    				created++;
    			}
    		}
    	} else {
    		String path = kind;
			
    		boolean success = this.makeRESTCall(chatty, listener, path, resources, overrides);
    		if (success) {
				listener.getLogger().println(String.format(MessageConstants.CREATED_OBJ, path));
    			created = 1;
    		} else {
				listener.getLogger().println(String.format(MessageConstants.FAILED_OBJ, path));
    			failed = 1;
    		}
    	}

    	if (failed > 0) {
    		listener.getLogger().println(String.format(MessageConstants.EXIT_CREATE_BAD, created, failed));
			return false;
    	} else {
    		listener.getLogger().println(String.format(MessageConstants.EXIT_CREATE_GOOD, created));
    		return true;
    	}
	}
    

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftCreator}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
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

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckNamespace(value);
        }

        public FormValidation doCheckJsonyaml(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckJsonyaml(value);
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

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
            save();
            return super.configure(req,formData);
        }

    }


}
