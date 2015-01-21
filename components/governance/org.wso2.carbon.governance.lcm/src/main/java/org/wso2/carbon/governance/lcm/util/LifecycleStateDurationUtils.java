/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
 

package org.wso2.carbon.governance.lcm.util;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jaxen.JaxenException;
import org.wso2.carbon.governance.registry.extensions.aspects.utils.LifecycleConstants;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.utils.RegistryUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * This class holds the utility methods related to lifecycle stateDuration
 */
public class LifecycleStateDurationUtils {

    /**
     * Log variable used to  when
     */
    private static final Log LOG = LogFactory.getLog(LifecycleStateDurationUtils.class);

    /**
     * This method is used to get CurrentLifecycleStateDuration from registry history
     * @param registryPathToResource  registry path to the resource
     * @param lifecycleName name of the lifecycle
     * @param registry      root registry object
     * @return  currentLifeCycleStateDuration   duration of the lifecycle state since last state update
     * @throws org.wso2.carbon.registry.core.exceptions.RegistryException
     */
    public static long getCurrentLifecycleStateDuration(String registryPathToResource, String lifecycleName,
            Registry registry) throws RegistryException {

        long currentLifeCycleStateDuration = 0;

        if (!StringUtils.isEmpty(registryPathToResource)) {
            String historyResourcePath = LifecycleConstants.LOG_DEFAULT_PATH + registryPathToResource
                    .replaceAll("/", "_");
            UserRegistry userRegistry = (UserRegistry) registry;
            if (userRegistry.resourceExists(historyResourcePath)) {
                String textContent = RegistryUtils.decodeBytes((byte[]) userRegistry.get(historyResourcePath)
                        .getContent());
                if (!StringUtils.isEmpty(textContent)) {
                    // Reading the history file and generating a document element
                    OMElement documentElement;
                    try {
                        documentElement = new StAXOMBuilder(new ByteArrayInputStream(textContent.getBytes()))
                                .getDocumentElement();
                    } catch (XMLStreamException e) {
                        String message = "Error while generating history file document element from " + textContent;
                        LOG.error(message, e);
                        throw new RegistryException(message, e);
                    }

                    String XpathString;
                    // Selecting 'item' nodes with an attribute 'targetState'
                    AXIOMXPath targetStateXpath;
                    try {
                        XpathString = LifecycleConstants.HISTORY_ITEM_TARGET_STATE_XPATH + "[" +
                                LifecycleConstants.HISTORY_ITEM_LIFECYCLE_NAME_PARAMETER + "='" + lifecycleName + "']";
                        targetStateXpath = new AXIOMXPath(XpathString);
                    } catch (JaxenException e) {
                        String message = "Error while getting " + LifecycleConstants.HISTORY_ITEM_TARGET_STATE_XPATH
                                + " from document element generated from " + historyResourcePath;
                        LOG.error(message, e);
                        throw new RegistryException(message, e);
                    }
                    // Selecting the nodes from document element by target state Xpath
                    List transitionNodesList;
                    try {
                        transitionNodesList = targetStateXpath.selectNodes(documentElement);
                    } catch (JaxenException e) {
                        String message = "Error while selecting nodes relevant to Xpath: " + LifecycleConstants
                                .HISTORY_ITEM_TARGET_STATE_XPATH + " from document element generated from " +
                                historyResourcePath;
                        LOG.error(message, e);
                        throw new RegistryException(message, e);
                    }

                    /**
                     * Getting the latest updated note to an OM element.
                     * If loop runs when there is a lifecycle state has changed from initial state, and else loop runs
                     * in initial life cycle state when there is no lifecycle state change.
                     */
                    OMElement omElement;
                    if (transitionNodesList != null && !transitionNodesList.isEmpty()) {
                        // First node (index 0) is selected because the latest updated history is stored in first node
                        omElement = (OMElement) transitionNodesList.get(0);
                    } else {
                        // Changing the Xpath to timestamp
                        try {
                            XpathString = LifecycleConstants.HISTORY_ITEM_TIME_STAMP_XPATH + "[" +
                                    LifecycleConstants.HISTORY_ITEM_LIFECYCLE_NAME_PARAMETER + "='" + lifecycleName +
                                    "']";
                            targetStateXpath = new AXIOMXPath(XpathString);
                        } catch (JaxenException e) {
                            String message = "Error while generating value relevant to Xpath: " + LifecycleConstants
                                    .HISTORY_ITEM_TIME_STAMP_XPATH;
                            LOG.error(message, e);
                            throw new RegistryException(message, e);
                        }
                        // Selecting timestamp from item nodes by timestamp Xpath
                        try {
                            transitionNodesList = targetStateXpath.selectNodes(documentElement);
                        } catch (JaxenException e) {
                            String message = "Error while generating history file document element from " + textContent;
                            LOG.error(message, e);
                            throw new RegistryException(message, e);
                        }
                        /**
                         * At this stage transitionNodesList doesn't get null values because it always has a history
                         * entry as item with timestamp.
                         * Getting the latest lifecycle history item node.
                         */
                        omElement = (OMElement) transitionNodesList.get(transitionNodesList.size() - 1);
                    }
                    String lastStateChangedTime = omElement.getAttribute(new QName(LifecycleConstants
                            .HISTORY_ITEM_TIME_STAMP)).getAttributeValue();
                    // Getting current time
                    Date currentTimeStamp = new Date();
                    SimpleDateFormat dateFormat = new SimpleDateFormat(LifecycleConstants
                            .HISTORY_ITEM_TIME_STAMP_FORMAT);
                    String currentTime = dateFormat.format(currentTimeStamp);
                    try {
                        // Calculating the time difference
                        currentLifeCycleStateDuration = calculateTimeDifference(currentTime, lastStateChangedTime);
                    } catch (RegistryException e) {
                        String message = "Error occurred when calculating the timestamp difference";
                        LOG.error(message, e);
                        throw new RegistryException(message, e);
                    }
                }
            } else {
                String message = "Resource: " + historyResourcePath + " does not exits";
                LOG.error(message);
                throw new RegistryException(message);
            }
        } else {
            String message = "Registry path to resource:" + registryPathToResource + " does not exits";
            LOG.error(message);
            throw new RegistryException(message);
        }
        return currentLifeCycleStateDuration;
    }

    /**
     * This method used to calculate time difference of two timestamps.
     * @param timeStampOne  latest timestamp.
     * @param timeStampTwo  earlier timestamp.
     * @return timeDurationTimestamp timestamp difference from current time to current lifecycle last state changed
     * time.
     */
    private static long calculateTimeDifference(String timeStampOne, String timeStampTwo) throws RegistryException {
        long timeDurationTimestamp;
        if (!StringUtils.isEmpty(timeStampOne) && !StringUtils.isEmpty(timeStampTwo)) {
            timeDurationTimestamp = Timestamp.valueOf(timeStampOne).getTime() - Timestamp.valueOf(timeStampTwo)
                    .getTime();
        } else {
            String message = "Timestamp one or timestamp two is not set";
            LOG.error(message);
            throw new RegistryException(message);
        }
        return timeDurationTimestamp;
    }
}
