package com.meterian.common.system;

import java.util.Map;

public class OS {
    
    public enum Family {windows, unix, mac, unknown};
    
    public String getenv(String name) {
        return System.getenv(name);
    }
    
    public Map<String,String> getenv() {
        return System.getenv();
    }

    public Family family() {
        final String osname = System.getProperty("os.name").toLowerCase();
        if (osname.indexOf("win") >= 0)
            return Family.windows;

        if (osname.indexOf("mac") >= 0)
            return Family.mac;

        if (osname.indexOf("nix") >= 0 || osname.indexOf("nux") >= 0 || osname.indexOf("aix") > 0 )
            return Family.unix;

        return Family.unknown;
    }
}
