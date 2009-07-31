package com.googlecode.wargo.winstone;

import winstone.Launcher;

import java.util.HashMap;
import java.util.Map;

public class WinstoneStandaloneServer implements Runnable {
    public void run() {

        Launcher winstone = null;
        try {
            Map args = new HashMap();
            args.put("warfile", wargo.WarGo_Start.war.getAbsolutePath());
            args.put("httpPort", "" + wargo.WarGo_Start.port);
            if (!"/".equals(wargo.WarGo_Start.context)) {
                args.put("prefix", wargo.WarGo_Start.context);
            }
            Launcher.initLogger(args);
            winstone = new Launcher(args);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        } finally {
            wargo.WarGo_Start.serverStarted();
        }

        if (winstone != null) {
            winstone.shutdown();
        }
    }
}
