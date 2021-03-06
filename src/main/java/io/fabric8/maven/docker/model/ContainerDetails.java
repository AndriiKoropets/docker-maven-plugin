package io.fabric8.maven.docker.model;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.util.*;


public class ContainerDetails implements Container {

    static final String CONFIG = "Config";
    static final String CREATED = "Created";
    static final String HOST_IP = "HostIp";
    static final String HOST_PORT = "HostPort";
    static final String ID = "Id";
    static final String IMAGE = "Image";
    static final String LABELS = "Labels";
    static final String NAME = "Name";
    static final String IP = "IPAddress";
    static final String NETWORK_SETTINGS = "NetworkSettings";
    static final String NETWORKS = "Networks";
    static final String PORTS = "Ports";
    static final String SLASH = "/";
    static final String STATE = "State";
    static final String HEALTH = "Health";
    static final String STATUS = "Status";
    static final String HEALTH_STATUS_HEALTHY = "healthy";
    static final String HEALTHCHECK = "Healthcheck";
    static final String TEST = "Test";

    private static final String EXIT_CODE = "ExitCode";
    private static final String RUNNING = "Running";

    private final JSONObject json;

    public ContainerDetails(JSONObject json) {
        this.json = json;
    }

    @Override
    public long getCreated() {
        String date = json.getString(CREATED);
        Calendar cal = DatatypeConverter.parseDateTime(date);
        return cal.getTimeInMillis();
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.getString(ID).substring(0, 12);
    }

    @Override
    public String getImage() {
        // ID: json.getString("Image");
        return json.getJSONObject(CONFIG).getString(IMAGE);
    }

    @Override
    public Map<String, String> getLabels() {
        JSONObject config = json.getJSONObject(CONFIG);
        return config.has(LABELS) ?
                mapLabels(config.getJSONObject(LABELS)) :
                Collections.<String, String>emptyMap();
    }

    @Override
    public String getName() {
        String name = json.getString(NAME);

        if (name.startsWith(SLASH)) {
            name = name.substring(1);
        }
        return name;
    }

    @Override
    public String getIPAddress() {
        if (json.has(NETWORK_SETTINGS) && !json.isNull(NETWORK_SETTINGS)) {
            JSONObject networkSettings = json.getJSONObject(NETWORK_SETTINGS);
            if (!networkSettings.isNull(IP)) {
                return networkSettings.getString(IP);
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getCustomNetworkIpAddresses() {
        if (json.has(NETWORK_SETTINGS) && !json.isNull(NETWORK_SETTINGS)) {
            JSONObject networkSettings = json.getJSONObject(NETWORK_SETTINGS);
            if (networkSettings.has(NETWORKS) && !networkSettings.isNull(NETWORKS)) {
                return extractNetworks(networkSettings);
            }
        }
        return null;
    }

    private Map<String, String> extractNetworks(JSONObject networkSettings) {
        JSONObject networks = networkSettings.getJSONObject(NETWORKS);
        JSONArray keys = networks.names();
        if (keys == null || keys.length() == 0) {
            return null;
        }
        Map<String, String> results = new HashMap<>();
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.getString(i);
            JSONObject net = networks.getJSONObject(key);
            if (net.has(IP) && !net.isNull(IP)) {
                results.put(key, net.getString(IP));
            }
        }

        return results;
    }

    @Override
    public Map<String, PortBinding> getPortBindings() {
        if (json.has(NETWORK_SETTINGS) && !json.isNull(NETWORK_SETTINGS)) {
            JSONObject networkSettings = json.getJSONObject(NETWORK_SETTINGS);
            if (!networkSettings.isNull(PORTS)) {
                return createPortBindings(networkSettings.getJSONObject(PORTS));
            }
        }

        return new HashMap<>();
    }

    @Override
    public boolean isRunning() {
        JSONObject state = json.getJSONObject(STATE);
        return state.getBoolean(RUNNING);
    }

    @Override
    public Integer getExitCode() {
        if (isRunning()) {
            return null;
        }
        JSONObject state = json.getJSONObject(STATE);
        return state.getInt(EXIT_CODE);
    }

    public boolean isHealthy() {
        final JSONObject state = json.getJSONObject(STATE);
        // always indicate healthy for docker hosts that do not support health checks.
        return !state.has(HEALTH) || HEALTH_STATUS_HEALTHY.equals(state.getJSONObject(HEALTH).getString(STATUS));
    }

    public String getHealthcheck() {
        if (!json.getJSONObject(CONFIG).has(HEALTHCHECK) ||
            !json.getJSONObject(CONFIG).getJSONObject(HEALTHCHECK).has(TEST)) {
            return null;
        }
        return json.getJSONObject(CONFIG).getJSONObject(HEALTHCHECK).getJSONArray(TEST).join(", ");
    }

    private void addPortMapping(String port, JSONObject hostConfig, Map<String, PortBinding> portBindings) {
        String hostIp = hostConfig.getString(HOST_IP);
        Integer hostPort = Integer.valueOf(hostConfig.getString(HOST_PORT));

        addPortMapping(port, new PortBinding(hostPort, hostIp), portBindings);
    }

    private void addPortMapping(String port, PortBinding binding, Map<String, PortBinding> portBindings) {
        if (port.indexOf('/') == -1) {
            port = port + "/tcp";
        }

        portBindings.put(port, binding);
    }

    private Map<String, PortBinding> createPortBindings(JSONObject ports) {
        Map<String, PortBinding> portBindings = new HashMap<>();

        for (Object obj : ports.keySet()) {
            String port = obj.toString();
            if (ports.isNull(port)) {
                addPortMapping(port, (PortBinding) null, portBindings);
            } else {
                // use the first entry in the array
                JSONObject hostConfig = ports.getJSONArray(port).getJSONObject(0);
                addPortMapping(port, hostConfig, portBindings);
            }
        }

        return portBindings;
    }

    private Map<String, String> mapLabels(JSONObject labels) {
        int length = labels.length();
        Map<String, String> mapped = new HashMap<>(length);

        Iterator<String> iterator = labels.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            mapped.put(key, labels.get(key).toString());
        }

        return mapped;
    }
}
