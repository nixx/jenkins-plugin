package com.openshift.jenkins.plugins.pipeline;

import java.io.IOException;
import java.io.Serializable;

import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import jenkins.tasks.SimpleBuildStep;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;

public abstract class OpenShiftBaseStep extends Builder  implements SimpleBuildStep, Serializable, IOpenShiftPlugin {
	
    protected final String apiURL;
    protected final String namespace;
    protected final String authToken;
    protected final String verbose;
    // marked transient so don't serialize these next 2 in the workflow plugin flow; constructed on per request basis
    protected transient TokenAuthorizationStrategy bearerToken;
    protected transient Auth auth;
    
    protected OpenShiftBaseStep(String apiURL, String namespace, String authToken, String verbose) {
    	this.apiURL = apiURL;
    	this.namespace = namespace;
    	this.authToken = authToken;
    	this.verbose = verbose;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

    public String getApiURL() {
    	if (apiURL == null)
    		return "";
		return apiURL;
	}

	public String getNamespace() {
		if (namespace == null)
			return "";
		return namespace;
	}
	
	public String getAuthToken() {
		if (authToken == null)
			return "";
		return authToken;
	}
	
    public String getVerbose() {
    	if (verbose == null)
    		return "";
		return verbose;
	}
    
    @Override
	public void setAuth(Auth auth) {
		this.auth = auth;
	}

	@Override
	public Auth getAuth() {
		return auth;
	}

	@Override
	public TokenAuthorizationStrategy getToken() {
		return bearerToken;
	}

	@Override
	public void setToken(TokenAuthorizationStrategy token) {
		this.bearerToken = token;
	}

	@Override
	public String getBaseClassName() {
		return OpenShiftBaseStep.class.getName();
	}

	// this is the workflow plugin path
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		this.doIt(run, workspace, launcher, listener);
	}

	// this is the classic jenkins build step path
	@Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		return this.doIt(build, launcher, listener);
    }


}
