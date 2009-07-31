package wargo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.MessageFormat;

public class WarGo_Start {

    private static final String BANNER =
            "\n" +
            "                 ,.   ,   ,.         ,---.      \n" +
            "                 `|  /|  /   ,-. ,-. |  -'  ,-. \n" +
            "launched by...    | / | /    ,-| |   |  ,-' | | \n" +
            "                  `'  `'     `-^ '   `---|  `-' \n" +
            "                                      ,-.|      \n" +
            "                                      `-+'      \n" +
            "\n";

    private static final String USAGE =
            "USAGE:      java -jar {0} [OPTIONS]\n" +
            "\n" +
            "OPTIONS:\n" +
            "    --port=PORT\n" +
            "            use PORT as the HTTP port\n" +
            "    --context=CONTEXT\n" +
            "            run the webapp under the context CONTEXT\n" +
            "    --usage\n" +
            "            print this usage statement and exit";

    private static final Pattern PORT_PARAM_PATTERN = Pattern.compile("--port=(\\d+)");
    private static final Pattern CONTEXT_PARAM_PATTERN = Pattern.compile("--context=/?(.*)");

    public static File war;
    public static int port = 8080;
    public static String context = "/";

    public static void main(String[] args) {

        System.out.println(BANNER);

        try {
            // get the .class file for THIS class as a resource, then use that URL to get a handle
            // on the jar file that contains it -- that jar file will be the WAR that has been
            // prepared with WarGo, which we will attempt to launch.
            JarFile archive = ((JarURLConnection) WarGo_Start.class.getClassLoader()
                    .getResource("wargo/WarGo_Start.class")
                    .openConnection()).getJarFile();
            war = new File(archive.getName());
            Attributes manifest = archive.getManifest().getMainAttributes();

            // allow the end user to override runtime arguments
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                Matcher matcher;
                if ((matcher = PORT_PARAM_PATTERN.matcher(arg)).matches()) {
                    port = Integer.valueOf(matcher.group(1)).intValue();
                } else if ((matcher = CONTEXT_PARAM_PATTERN.matcher(arg)).matches()) {
                    context = "/" + matcher.group(1);
                } else if ("--usage".equals(arg)) {
                    System.out.println(MessageFormat.format(USAGE, new Object[] {war.getName()}));
                    System.exit(0);
                } else {
                    System.out.println("unrecognized option : " + arg);
                    System.out.println(MessageFormat.format(USAGE, new Object[] {war.getName()}));
                    System.exit(-1);
                }
            }

            // create a new ClassLoader that loads classes from the /META-INF/wargo-classes
            // directory inside the war, with this class's ClassLoader as the parent
            ClassLoader classLoader = new URLClassLoader(
                    new URL[]{
                            new URL("jar:" + war.toURI().toString() + "!/META-INF/wargo-classes/")
                    },
                    WarGo_Start.class.getClassLoader());

            // the standalone server must implement Runnable - instantiate it reflectively and start
            Thread standaloneServerThread = new Thread(
                    (Runnable) classLoader.loadClass(
                            manifest.getValue("WarGo-Standalone-Server")).newInstance());
            standaloneServerThread.start();

            // wait for the server to start, then present a way for the server to be stopped, then
            // finally signal the server to stop once the user has stopped it
            executeServerLifecycle();

            // join the server thread to let it shut down cleanly
            standaloneServerThread.join();

            // when we're done - exit.
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static Boolean done = null;

    private static void executeServerLifecycle() throws IOException {
        // wait until the server has started - indicated by the done field being initialized to
        // Boolean.FALSE
        while (done == null) {
            synchronized (WarGo_Start.class) {
                try {
                    WarGo_Start.class.wait();
                } catch (InterruptedException e) {
                    // do nothing.
                }
            }
        }

        // wait for the user to stop the server, then indicate that by marking done = Boolean.TRUE
        // and calling notify
        System.out.println("server started - type 'exit' to stop the server.");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = null;
        while (!"exit".equals((line = reader.readLine()))) { }

        synchronized (WarGo_Start.class) {
            done = Boolean.TRUE;
            WarGo_Start.class.notifyAll();
        }
    }


    public static void serverStarted() {
        // indicate to the main thread that the server has been started
        synchronized (WarGo_Start.class) {
            done = Boolean.FALSE;
            WarGo_Start.class.notifyAll();
        }

        // wait for the server to stop, indicated by done being set to Boolean.TRUE
        while (!done.booleanValue()) {
            synchronized (WarGo_Start.class) {
                try {
                    WarGo_Start.class.wait();
                } catch (InterruptedException e) {
                    // do nothing.
                }
            }
        }
    }
}
