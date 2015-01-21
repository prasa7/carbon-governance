/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.governance.custom.lifecycles.checklist.ui.clients;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.governance.lcm.stub.LifeCycleManagementServiceRegistryExceptionException;
import org.wso2.carbon.governance.lcm.stub.LifeCycleManagementServiceStub;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;

/**
 * This class contains the client to call lifecycle management service and get data.
 */
public class LifeCycleManagementServiceClient {

    /**
     * LOG object to use when logging is required in this class.
     */
    private static final Log LOG = LogFactory.getLog(LifeCycleManagementServiceClient.class);

    /**
     * Lifecycle management service name.
     */
    private static final String SERVICE_NAME = "LifeCycleManagementService";

    /**
     * Duration seconds format.
     * This will produce a two digit number for seconds followed with character 's'.
     * Example 08s.
     */
    private static final String DURATION_SECONDS_FORMAT = "%02ds";

    /**
     * Duration minutes and seconds format.
     * This will provide a two digit number for minutes followed by character 'm' and a two digit number for seconds
     * followed with character 's'.
     * Example: 01m:23s.
     */
    private static final String DURATION_MINUTES_SECONDS_FORMAT = "%02dm:%02ds";

    /**
     * Duration hours, minutes and seconds format. This will provide a two digit number for hours followed by
     * character 'h', a two digit number for minutes followed with character 'm' and a two digit number for seconds
     * followed with character 's'.
     * Example 07h:12m:09s.
     */
    private static final String DURATION_HOURS_MINUTES_SECONDS_FORMAT = "%02dh:%02dm:%02ds";

    /**
     * Duration days, hours, minutes and seconds format.
     * This will produce a number for days followed with character 'd', a two digit number for hours followed by
     * character 'h', a two digit number for minutes followed with character 'm' and a two digit number for seconds
     * followed with character 's'.
     */
    private static final String DURATION_DAYS_HOURS_MINUTES_SECONDS_FORMAT = "%dd:%02dh:%02dm:%02ds";

    /**
     * Stub object of lifecycle management service.
     * This is used to call to LifeCycleManagementService service operations.
     */
    public static LifeCycleManagementServiceStub stub;

    /**
     * End point reference to the service.
     * This is used when colling the service.
     */
    private String endPointReference;

    /**
     * Constructor to initialize lifecycle management service client
     * @param cookie    session cookie
     * @param backendServerURL  backend service URL
     * @param configContext Configuration context
     * @throws RegistryException
     */
    public LifeCycleManagementServiceClient(
            String cookie, String backendServerURL, ConfigurationContext configContext)
            throws RegistryException {
        endPointReference = backendServerURL + SERVICE_NAME;
        try {
            stub = new LifeCycleManagementServiceStub(configContext, endPointReference);
            ServiceClient client = stub._getServiceClient();
            Options option = client.getOptions();
            option.setManageSession(true);
            option.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING, cookie);
        } catch (AxisFault axisFault) {
            String msg = "Failed to initiate lifecycle management service client. " + axisFault.getMessage();
            LOG.error(msg, axisFault);
            throw new RegistryException(msg, axisFault);
        }
    }

    /**
     * This client side method to get current lifecycle state duration from lifecycle management service.
     * @param resourcePath  registry path to the resource.
     * @param lifecycleName lifecycle name associated to the resource. In multiple lifecycle scenario this service is
     *                      called once at a time.
     * @return lifecycle current lifecycle state duration.
     * @throws RegistryException
     */
    public String getLifecycleCurrentStateDuration(String resourcePath, String lifecycleName)
            throws RegistryException {
        String timeDuration;
        if (!StringUtils.isEmpty(resourcePath) && !StringUtils.isEmpty(lifecycleName)) {
            try {
                long timeDurationTimestamp = stub.getLifecycleCurrentStateDuration(resourcePath, lifecycleName);
                // Format time duration to dd:hh:mm:ss
                timeDuration = formatTimeDuration(timeDurationTimestamp);
            } catch (RemoteException e) {
                String message = SERVICE_NAME + "'s operation, getLifecycleCurrentStateDuration is not unavailable";
                LOG.error(message, e);
                throw new RegistryException(message, e);
            } catch (LifeCycleManagementServiceRegistryExceptionException e) {
                String message =
                        "Error in service: " + SERVICE_NAME + " while getting lifecycle current state duration";
                LOG.error(message, e);
                throw new RegistryException(message, e);
            }
        } else {
            String message = "Lifecycle directory path is or lifecycle name is not set";
            LOG.error(message);
            throw new RegistryException(message);
        }
        return timeDuration;
    }

    /**
     * This method is used to format a timestamp to 'dd:hh:mm:ss'.
     * @param duration  timestamp duration
     * @return formatted time duration to 'dd:hh:mm:ss'
     */
    public String formatTimeDuration(long duration) {
        String timeDuration;
        long days = TimeUnit.MILLISECONDS.toDays(duration);
        long hours = TimeUnit.MILLISECONDS.toHours(duration) - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS
                .toDays(duration));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
                .toHours(duration));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                .toMinutes(duration));
        // Setting the duration to a readable format
        if (days == 0 && hours == 0 && minutes == 0) {
            timeDuration = String.format(DURATION_SECONDS_FORMAT, seconds);
        } else if (days == 0 && hours == 0) {
            timeDuration = String.format(DURATION_MINUTES_SECONDS_FORMAT, minutes, seconds);
        } else if (days == 0) {
            timeDuration = String.format(DURATION_HOURS_MINUTES_SECONDS_FORMAT, hours, minutes, seconds);
        } else {
            timeDuration = String.format(DURATION_DAYS_HOURS_MINUTES_SECONDS_FORMAT, days, hours, minutes, seconds);
        }
        return timeDuration;
    }
}
