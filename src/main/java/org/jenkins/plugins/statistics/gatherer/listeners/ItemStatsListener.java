package org.jenkins.plugins.statistics.gatherer.listeners;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.User;
import hudson.model.listeners.ItemListener;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import org.jenkins.plugins.statistics.gatherer.model.job.JobStats;
import org.jenkins.plugins.statistics.gatherer.util.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;
import org.json.XML;

/**
 * Created by hthakkallapally on 3/12/2015.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES)
public class ItemStatsListener extends ItemListener {
    private static final Logger LOGGER = Logger.getLogger(ItemStatsListener.class.getName());

    public ItemStatsListener() {
        //Necessary for jenkins
    }

    @Override
    public void onCreated(Item item) {
        if (PropertyLoader.getProjectInfo() && canHandle(item)) {
            try {
                AbstractItem project = asProject(item);
                JobStats ciJob = addCIJobData(project);
                ciJob.setCreatedDate(new Date());
                ciJob.setStatus(Constants.ACTIVE);
                setConfig(project, ciJob);
                RestClientUtil.postToService(getRestUrl(), ciJob);
                SnsClientUtil.publishToSns(ciJob);
                LogbackUtil.info(ciJob);
            } catch (Exception e) {
                logException(item, e);
            }
        }
    }

    private AbstractItem asProject(Item item) {
        if(canHandle(item)) {
            return (AbstractItem) item;
        } else {
            throw new IllegalArgumentException("Discarding item " + item.getDisplayName() + "/" + item.getClass() + " because it is not an AbstractItem");
        }
    }

    private boolean canHandle(Item item) {
        return item instanceof AbstractItem;
    }

    private void logException(Item item, Exception e) {
        LOGGER.log(Level.WARNING, "Failed to call API " + getRestUrl() +
                " for job " + item.getDisplayName(), e);
    }

    /**
     * Construct REST API url for project resource.
     *
     * @return
     */
    private String getRestUrl() {
        return PropertyLoader.getProjectEndPoint();
    }

    /**
     * Construct CIJob model and populate common data in helper method.
     *
     * @param project
     * @return
     */
    private JobStats addCIJobData(AbstractItem project) {
        JobStats ciJob = new JobStats();
        ciJob.setCiUrl(Jenkins.getInstance().getRootUrl());
        ciJob.setName(project.getName());
        ciJob.setJobUrl(project.getUrl());
        String userName = Jenkins.getAuthentication().getName();
        User user = Jenkins.getInstance().getUser(userName);
        if(user != null) {
            ciJob.setUserId(user.getId());
            ciJob.setUserName(user.getFullName());
        }

        return ciJob;
    }

    /**
     * Get job configuration as a string and store it in DB.
     *
     * @param project
     * @param ciJob
     */
    private void setConfig(AbstractItem project, JobStats ciJob) {
        try {
            ciJob.setConfigFile(project.getConfigFile().asString());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to get config.xml file " +
                    " for " + project.getDisplayName(), e);
        }
    }

    /**
     * check if project disabled
     *
     * @param project
     * @return
     */

    private boolean isDisabled(AbstractItem project) {
       boolean is_disabled = false;
       try {
            JSONObject xmlJSONObj = XML.toJSONObject(project.getConfigFile().asString());
            Iterator<String> keys = xmlJSONObj.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                if (xmlJSONObj.get(key) instanceof JSONObject) {
                    JSONObject data = (JSONObject) xmlJSONObj.get(key);
                    if (data.has("disabled")) {
                        is_disabled = (boolean) data.get("disabled");
                        return is_disabled;
                    }
                }
            }
       }
       catch (Exception e) {
           logException(project, e);
       }
       return is_disabled;
    }

    @Override
    public void onUpdated(Item item) {
        if (PropertyLoader.getProjectInfo() && canHandle(item)) {
            AbstractItem project = asProject(item);
            try {
                JobStats ciJob = addCIJobData(project);
                ciJob.setUpdatedDate(new Date());
                ciJob.setStatus(isDisabled(project) ? Constants.DISABLED : Constants.ACTIVE);
                setConfig(project, ciJob);
                RestClientUtil.postToService(getRestUrl(), ciJob);
                SnsClientUtil.publishToSns(ciJob);
                LogbackUtil.info(ciJob);
            } catch (Exception e) {
                logException(item, e);
            }
        }
    }

    @Override
    public void onDeleted(Item item) {
        if (PropertyLoader.getProjectInfo() && canHandle(item)) {
            AbstractItem project = asProject(item);
            try {
                JobStats ciJob = addCIJobData(project);
                ciJob.setUpdatedDate(new Date());
                ciJob.setStatus(Constants.DELETED);
                RestClientUtil.postToService(getRestUrl(), ciJob);
                SnsClientUtil.publishToSns(ciJob);
                LogbackUtil.info(ciJob);
            } catch (Exception e) {
                logException(item, e);
            }
        }
    }
}