package com.lobsterxie.cvs.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务器配置类 - 静态内部类
 */
public class ServerConfig {
    private String tag;
    private String cvsRoot;
    private String description;
    private char[] password;
    private boolean savePassword;
    private Map<String, String> properties = new HashMap<>();

    public ServerConfig(String tag, String cvsRoot) {
        this.tag = tag;
        this.cvsRoot = cvsRoot;
    }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getCvsRoot() { return cvsRoot; }
    public void setCvsRoot(String cvsRoot) { this.cvsRoot = cvsRoot; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public char[] getPassword() { return password != null ? password.clone() : null; }
    public void setPassword(char[] password) {
        this.password = password != null ? password.clone() : null;
    }

    public boolean isSavePassword() { return savePassword; }
    public void setSavePassword(boolean savePassword) { this.savePassword = savePassword; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    public void addProperty(String key, String value) {
        properties.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("ServerConfig{tag='%s', cvsRoot='%s', description='%s', savePassword=%s}",
                tag, cvsRoot, description, savePassword);
    }
}
