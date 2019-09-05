package entries;

import util.Config;
import util.ResourcesLoader;

import java.io.*;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class AutoTester {

    private final Timer timer;
    private final int startTag;
    private final int endTag;
    private final int delay;
    private int currentTag;
    private final TestCallBack callBack = AutoTester.this::start;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Invalid Arguments. Try: dummydht.jar -a <startTag> <endTag> <delay>");
            return;
        }
        int startTag = Integer.valueOf(args[0]);
        int endTag = Integer.valueOf(args[1]);
        int delay = Integer.valueOf(args[2]);
        AutoTester tester = new AutoTester(startTag, endTag, delay);
        tester.start();
    }

    public AutoTester(int startTag, int endTag, int delay) {
        timer = new Timer();
        this.startTag = startTag;
        this.endTag = endTag;
        this.delay = delay;
        this.currentTag = startTag;
    }

    private void start() {
        try {
            Properties prop = new Properties();
            String propPath = ResourcesLoader.getRelativePathToRes("config.properties");
            InputStream in = new FileInputStream(propPath);
            prop.load(in);
            if (currentTag > endTag) {
                System.out.println("All tests finished!");
                System.exit(0);
                return;
            }
            prop.setProperty(Config.PROPERTY_TRIAL_TAG, String.valueOf(currentTag));
            prop.store(new FileOutputStream(propPath), null);
            System.out.println("Tag updated...");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Task scheduled...");
        timer.schedule(new TestTask(callBack), delay * 60 * 1000);
    }

    class TestTask extends TimerTask {

        private final TestCallBack testCallBack;

        public TestTask(TestCallBack callBack) {
            this.testCallBack = callBack;
        }

        @Override
        public void run() {
            try {
                String[] cmd = new String[]{"/bin/sh", ResourcesLoader.getRelativeFileName(ScriptGenerator.FILE_UPDATE_ALL)};
                await(cmd);
                System.out.println("Files updated...");
                // Thread.sleep(3 * 60 * 1000);

                cmd = new String[]{"/bin/sh", ResourcesLoader.getRelativeFileName(ScriptGenerator.FILE_START_ALL)};
                await(cmd);
                System.out.println("Daemons started...");

                cmd = new String[]{"/bin/sh", ResourcesLoader.getRelativeFileName(ScriptGenerator.FILE_START_PROXY)};
                await(cmd);
                System.out.println("Proxy started...");
                Thread.sleep(5 * 1000);

                System.out.println("Launch client...");
                // RegularClient.main(new String[]{ "-r", ResourcesLoader.getParentDirOfProgramPath() + File.separator + "test" + File.separator + "full5.txt" });
                cmd = new String[]{"java", "-jar", "dummydht.jar", "-c", "-r", ResourcesLoader.getParentDirOfProgramPath() + File.separator + "test" + File.separator + "full5.txt"};
                await(cmd);
                //Runtime.getRuntime().exec("java -jar dummydht.jar -c -r ~/test/full5.txt").waitFor();

                cmd = new String[]{"/bin/sh", ResourcesLoader.getRelativeFileName(ScriptGenerator.FILE_STOP_ALL_BUT_CLIENT)};
                await(cmd);
                System.out.println("Test[" + currentTag +  "] done!");

                currentTag++;
                if (testCallBack != null)
                    testCallBack.onTestFinished();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void await(String[] cmd) {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            try {
                Process process = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null)
                    System.out.println(line);
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    interface TestCallBack {
        void onTestFinished();
    }
}
