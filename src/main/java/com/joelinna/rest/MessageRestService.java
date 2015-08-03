package com.joelinna.rest;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.*;
import com.joelinna.exception.RestServerException;
import org.apache.log4j.Logger;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Path("/message")
public class MessageRestService {
    private static final Logger LOGGER = Logger.getLogger(MessageRestService.class);
    private static final String CONFIG_PROPERTIES = "config.properties";

    private AmazonRoute53 amazonRoute53;
    private String ipServiceURL;
    private String hostedZone;

    @Context
    private ServletContext context;

    @GET
    @Path("/{recordName}")
    public Response printMessage(@PathParam("recordName") String recordName) {
        init();

        String publicIP = getPublicIP();
        String result = "myIP: " + publicIP;
        List<ResourceRecordSet> resourceRecordSets = getResourceRecordSets(recordName);
        result += " recordCount: " + resourceRecordSets.size();

        if (resourceRecordSets.size() <= 1 || resourceRecordSets.size() == 0) {
            addOrUpdateResourceRecordSet(recordName, RRType.A, publicIP);
        } else if (resourceRecordSets.size() > 1) {
            // Throw error, only should be one record
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
        }

        return Response.status(Response.Status.OK).entity(result).build();
    }

    public String getPublicIP() {
        String myIP = null;
        BufferedReader in = null;
        try {
            URL whatIsMyIP = new URL(ipServiceURL);
            in = new BufferedReader(new InputStreamReader(whatIsMyIP.openStream()));
            myIP = in.readLine();
            in.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                in = null;
            }
        }
        return myIP;
    }

    public List<ResourceRecordSet> getResourceRecordSets(String recordName) {
        // Setup getting of resource record set
        ListResourceRecordSetsRequest recordSetsRequest = new ListResourceRecordSetsRequest(hostedZone);
        recordSetsRequest.setStartRecordName(recordName);
        recordSetsRequest.setStartRecordType(RRType.A);
        recordSetsRequest.setMaxItems("1");

        ListResourceRecordSetsResult recordSetsResult = amazonRoute53.listResourceRecordSets(recordSetsRequest);

        List<ResourceRecordSet> resourceRecordSets = recordSetsResult.getResourceRecordSets();

        if (resourceRecordSets == null) {
            resourceRecordSets = new ArrayList<ResourceRecordSet>();
        }
        return resourceRecordSets;
    }

    public void addOrUpdateResourceRecordSet(String recordName, RRType recordType, String recordValue) {
        ResourceRecordSet recordSet = new ResourceRecordSet(recordName, recordType);
        ResourceRecord resourceRecord = new ResourceRecord(recordValue);
        List<ResourceRecord> resourceRecords = new ArrayList<ResourceRecord>(1);
        resourceRecords.add(resourceRecord);
        recordSet.setResourceRecords(resourceRecords);
        recordSet.setTTL(1200L);

        List<Change> changes = new ArrayList<Change>(1);
        Change change = new Change(ChangeAction.UPSERT, recordSet);
        changes.add(change);

        ChangeBatch batch = new ChangeBatch();
        batch.setChanges(changes);
        ChangeResourceRecordSetsRequest changeRequest = new ChangeResourceRecordSetsRequest(hostedZone, batch);

        ChangeResourceRecordSetsResult changeResult = amazonRoute53.changeResourceRecordSets(changeRequest);

        ChangeInfo changeInfo = changeResult.getChangeInfo();
        LOGGER.info("Change Info Status: " + changeInfo.getStatus());
        LOGGER.info("Change Info Comment: " + changeInfo.getComment());
    }

    private void init() {
        Properties props = readPropertyFile(CONFIG_PROPERTIES);
        ipServiceURL = props.getProperty("IP_SERVICE");
        if (ipServiceURL == null || ipServiceURL.trim().isEmpty()) {
            ipServiceURL = "http://checkip.amazonaws.com/";
        }
        hostedZone = props.getProperty("HOSTED_ZONE");
        if (hostedZone == null || hostedZone.trim().isEmpty()) {
            Map<String, String> env = System.getenv();
            if (env != null) {
                hostedZone = env.get("HOSTED_ZONE");
            }
            if (hostedZone == null || hostedZone.isEmpty()) {
                throw new RestServerException("Could not find value for HOSTED_ZONE in environmental variables or " + CONFIG_PROPERTIES);
            }
        }

        amazonRoute53 = new AmazonRoute53Client(new DefaultAWSCredentialsProviderChain());
    }

    private Properties readPropertyFile(String propertyFile) {
        Properties props = new Properties();
        InputStream inputStream = context.getResourceAsStream("/WEB-INF/classes/" + propertyFile);

        if (inputStream != null) {
            try {
                props.load(inputStream);
            } catch (IOException e) {
                throw new RestServerException("Error loading properties file", e);
            }
        } else {
            throw new RestServerException("Could not get inputStream for properties file");
        }
        return props;
    }
}