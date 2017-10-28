package org.maggus.gpusher;

import javax.swing.*;

public abstract class GWorker {
    private final SwingWorker worker;
    private final Main main;
    private long t0;

    GWorker(Main main) {
        this.main = main;
        worker = new SwingWorker<Void, Void>() {
            private Exception ex;

            @Override
            protected Void doInBackground() {
                ex = null;
                try {
                    GWorker.this.doInBackground();
                } catch (Exception ex) {
                    this.ex = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                GWorker.this.done(ex);
            }
        };
    }

    protected void prepare() {
        // prepare
        t0 = System.currentTimeMillis();
        //main.persist();
        GitRunner.setCommandValidator(main.new CommandsLogger());
        main.showProgressDialog(true);
    }

    protected  abstract void doInBackground() throws Exception;

    protected void done(Exception ex) {
        if (ex == null) {
            // done, all good
            if(t0 > 0){
                long t1 = System.currentTimeMillis();
                int ts = (int) ((t1 - t0) / 1000);
                String tsStr = null;
                if (ts == 0) {
                    //tsStr = "in less then a second.";
                } else if (ts == 1) {
                    tsStr = "in one second.";
                } else {
                    tsStr = "in " + ts + " seconds.";
                }
                if (tsStr != null) {
                    Log.log("Done " + tsStr);
                }
            }
            GitRunner.setCommandValidator(null);
            //main.updateGitStatus();
        } else {
            // done with error
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
        // finally
        GitRunner.setCommandValidator(null);
        main.showProgressDialog(false);
    }

    public void execute() {
        prepare();
        worker.execute();
    }
}
