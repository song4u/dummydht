package elastic;

import commonmodels.Terminal;

public class ElasticTerminal implements Terminal {

    @Override
    public void initialize() {
        ElasticCommand.INITIALIZE.execute(null);
    }

    @Override
    public void destroy() {
        ElasticCommand.DESTROY.execute(null);
    }

    @Override
    public void printInfo() {
        System.out.println("\nAvailable commands:\n" +
                ElasticCommand.READ.getHelpString() + "\n" +
                ElasticCommand.WRITE.getHelpString() + "\n" +
                ElasticCommand.ADDNODE.getHelpString() + "\n" +
                ElasticCommand.REMOVENODE.getHelpString() + "\n" +
                ElasticCommand.MOVEBUCKET.getHelpString() + "\n" +
                ElasticCommand.LISTPHYSICALNODES.getHelpString() + "\n" +
                ElasticCommand.PRINTLOOKUPTABLE.getHelpString() + "\n");
    }

    @Override
    public String execute(String[] args) {
        ElasticCommand cmd = ElasticCommand.valueOf(args[0].toUpperCase());
        return cmd.execute(args);
    }

    @Override
    public void bootstrap() {
        ElasticCommand.BOOTSTRAP.execute(null);
    }

}

