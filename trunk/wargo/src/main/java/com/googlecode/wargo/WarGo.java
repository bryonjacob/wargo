package com.googlecode.wargo;

import java.io.*;
import java.text.MessageFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class WarGo {

    private static final String DEFAULT_PROVIDER = "jetty";

    private File war;
    private File out;
    private String provider = DEFAULT_PROVIDER;
    private boolean verbose;
    private boolean debug;

    private void log(String message) {
        if (verbose) {
            writeLogMessage(message);
        }
    }

    protected void writeLogMessage(String message) {
        System.out.println(message);
    }

    public void execute() {
        // short-circuit to failure immediately if no war file was provided...
        if (war == null) {
            throw new RuntimeException("you must provide a war file to prepare");
        }

        // write out the configuration parameters
        log("preparing war with WarGo");
        log("");
        log("processing war:        " + war);
        log("writing output to:     " + (out == null ? war : out));
        log("debug:                 " + debug);
        log("provider:              " + provider);
        log("");

        // declare some oft-reused variables at the top
        int len;
        JarEntry entry;
        byte[] buff = new byte[4096];

        // open the original war file and the provider jar as JarInputStreams
        JarInputStream originalWar;
        try {
            originalWar = new JarInputStream(new FileInputStream(war));
        } catch (IOException e) {
            File param = war;
            String pattern = "war file {0} is not a valid war file";
            throw new RuntimeException(MessageFormat.format(pattern, new Object[]{param}), e);
        }
        JarInputStream provider;
        try {
            provider = new JarInputStream(getClass().getResourceAsStream(
                    MessageFormat.format("/META-INF/wargo-{0}-provider.jar",
                                         new Object[]{this.provider})));
        } catch (IOException e) {
            throw new RuntimeException(
                    MessageFormat.format("invalid provider {0}", new Object[]{this.provider}),
                    e);
        }

        // the new manifest will be a copy of the original war's manifest, with the additional
        // WarGo entries added:
        //  - the Main-Class entry will be set to WarGo_Start, to make sure the WarGo_Start class
        //    is the auto-start class for the war
        //  - the WarGo-Standalone-Server entry will be the fully-qualified class name of the
        //    provider class the user has selected to embed in the war
        Manifest manifest = originalWar.getManifest() == null ?
                            new Manifest() :
                            new Manifest(originalWar.getManifest());
        manifest.getMainAttributes().putValue("Main-Class", "wargo.WarGo_Start");
        manifest.getMainAttributes().putValue("WarGo-Standalone-Server",
                                              provider.getManifest().getMainAttributes().getValue(
                                                      "WarGo-Standalone-Server"));

        // open a new JarOutputStream to write out the new, WarGo-injected war
        File tempWar;
        try {
            tempWar = File.createTempFile("wargo", ".war");
        } catch (IOException e) {
            throw new RuntimeException("could not create temporary file", e);
        }
        if (debug) {
            log(MessageFormat.format("writing to temporary war {0}", new Object[]{tempWar}));
        } else {
            tempWar.deleteOnExit();
        }
        JarOutputStream wargoWar;
        try {
            wargoWar = new JarOutputStream(new FileOutputStream(tempWar), manifest);
        } catch (IOException e) {
            throw new RuntimeException(
                    MessageFormat.format("unable to write to temporary war {0}",
                                         new Object[]{tempWar}),
                    e);
        }

        // copy all of the entries from the original war file into the new one
        try {
            while ((entry = originalWar.getNextJarEntry()) != null) {
                log(MessageFormat.format("copying {0} into new war file",
                                         new Object[]{entry.getName()}));

                wargoWar.putNextEntry(entry);
                while ((len = originalWar.read(buff)) >= 0) {
                    wargoWar.write(buff, 0, len);
                }
                wargoWar.closeEntry();
                originalWar.closeEntry();
            }
            originalWar.close();
        } catch (IOException e) {
            throw new RuntimeException(
                    MessageFormat.format("error copying contents of {0} to {1}",
                                         new Object[]{war, tempWar}), e);
        }

        try {
            // inject anything in the wargo packages or in /META-INF/wargo-classes into the new war
            while ((entry = provider.getNextJarEntry()) != null) {
                if (entry.getName().matches("/?com/googlecode/wargo/.+")) {
                    entry = new JarEntry("META-INF/wargo-classes/" + entry.getName());
                } else if (!entry.getName().matches("/?META-INF/wargo-classes/.*")) {
                    continue;
                }

                log(MessageFormat.format("injecting {0} into new war file",
                                         new Object[]{entry.getName()}));

                wargoWar.putNextEntry(entry);
                while ((len = provider.read(buff)) >= 0) {
                    wargoWar.write(buff, 0, len);
                }
                wargoWar.closeEntry();
                provider.closeEntry();

            }
            provider.close();

            // we need to write an entry at the very top of the war which is the WarGo_Start class
            // itself - this is the class that is specified in the manifest as the Main-Class for the
            // war
            log("injecting wargo/WarGo_Start.class into new war file");
            InputStream startClass = getClass().getResourceAsStream("/wargo/WarGo_Start.class");
            wargoWar.putNextEntry(new JarEntry("wargo/WarGo_Start.class"));
            while ((len = startClass.read(buff)) >= 0) {
                wargoWar.write(buff, 0, len);
            }
            wargoWar.closeEntry();
            startClass.close();
        } catch (IOException e) {
            throw new RuntimeException(
                    MessageFormat.format("error injecting WarGo components into {0}",
                                         new Object[]{tempWar}),
                    e);
        }

        // close the writer to write the new war file
        try {
            wargoWar.close();
        } catch (IOException e) {
            throw new RuntimeException(
                    MessageFormat.format("error finalizing {0}", new Object[]{tempWar}),
                    e);
        }

        // finally, rename the temp file to the supplied name, or overwrite the existing file if
        // none provided
        tempWar.renameTo(out == null ? war : out);

        // write out success!
        log("successfully prepared!");
        log("");
        log("you should now be able to run your application like so:");
        log("");
        log("   java -jar " + out);

    }

    public void setWar(File war) {
        this.war = war;
    }

    public void setOut(File out) {
        this.out = out;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public static void main(String[] args) {

        WarGo warGo = new WarGo();

        for (int i = 0; i < args.length; i++) {
            if ("-v".equals(args[i]) || "--verbose".equals(args[i])) {
                warGo.setVerbose(true);
            } else if ("-q".equals(args[i]) || "--quiet".equals(args[i])) {
                warGo.setVerbose(false);
            } else if ("-d".equals(args[i]) || "--debug".equals(args[i])) {
                warGo.setDebug(true);
            } else if ("-o".equals(args[i]) || "--output".equals(args[i])) {
                if (++i >= args.length) {
                    fail("you must provide an argument to the " + args[i - 1] + " option!");
                }
                warGo.setOut(new File(args[i]));
            } else if ("-p".equals(args[i]) || "--provider".equals(args[i])) {
                if (++i >= args.length) {
                    fail("you must provide an argument to the " + args[i - 1] + " option!");
                }
                warGo.setProvider(args[i]);
            } else if (warGo.war == null) {
                warGo.setWar(new File(args[i]));
            } else {
                fail("unrecognized argument: " + args[i]);
            }
        }

        try {
            warGo.execute();
        } catch (RuntimeException e) {
            if (warGo.debug) {
                e.printStackTrace(System.out);
            } else {
                System.out.println(e.getMessage());
            }
            usage();
        }

    }

    private static void fail(String message) {
        System.out.println(message);
        usage();
    }

    private static void usage() {
        System.out.println(
                "\n" +
                "       ,.   ,   ,.         ,---.      \n" +
                "       `|  /|  /   ,-. ,-. |  -'  ,-. \n" +
                "        | / | /    ,-| |   |  ,-' | | \n" +
                "        `'  `'     `-^ '   `---|  `-' \n" +
                "                            ,-.|      \n" +
                "                            `-+'      \n" +
                "NAME\n" +
                "       wargo - prepare a war to be a self-executing file\n" +
                "\n" +
                "USAGE\n" +
                "       wargo [OPTIONS] WARFILE\n" +
                "\n" +
                "DESCRIPTION\n" +
                "       WarGo is a tool that turns a war archive (a Java web application) into\n" +
                "       a self-executing file.  WarGo embeds the binaries for a Servlet \n" +
                "       Container (currently Jetty or Winstone) into the war file itself, along\n" +
                "       with a \"start\" class that bootstraps execution.  The name of that\n" +
                "       start class is referenced in the Manifest file of the war, turning the\n" +
                "       war file into an executable jar.  After preparing \"myapp.war\" with\n" +
                "       WarGo, you can start the web application by running:\n" +
                "\n" +
                "           java -jar myapp.war\n" +
                "\n" +
                "WARFILE\n" +
                "       The WARFILE argument to WarGo is simply the path to the war file you\n" +
                "       wish to prepare.\n" +
                "\n" +
                "OPTIONS\n" +
                "       -v/--verbose\n" +
                "               print out a detailed trace of the operations that WarGo performs\n" +
                "\n" +
                "       -q/--quiet\n" +
                "               print as little as possible\n" +
                "\n" +
                "       -d/--debug\n" +
                "               in debug mode, the temporary file(s) that WarGo creates are not\n" +
                "               deleted if WarGo fails, and stack traces are printed in the case\n" +
                "               of any errors\n" +
                "\n" +
                "       -o/--output <FILENAME>\n" +
                "               generate a new war file named FILENAME rather than overwriting\n" +
                "               the original file (the default behavior)\n" +
                "\n" +
                "       -p/--provider <jetty|winstone>\n" +
                "               specify the provider to use (currently jetty or winstone) - the\n" +
                "               default is jetty\n" +
                "\n" +
                "EXAMPLES\n" +
                "       Some examples of running WarGo are:\n" +
                "\n" +
                "       wargo myapp.war\n" +
                "           This will prepare myapp.war to be launched in an embedded Jetty\n" +
                "           container.\n" +
                "\n" +
                "       wargo -p winstone -o wargo-myapp.war\n" +
                "           This will make a copy of myapp.war named wargo-myapp.war, which is\n" +
                "           prepared to be launched in an embedded Winstone container.\n");
    }
}
