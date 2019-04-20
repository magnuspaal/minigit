package client.commands;


import client.service.Init;

public class InitCommand extends Command {
    @Override
    public void process(String[] input) throws Exception {
        if (input.length == 2) {
            Init.initRepository(input[1]);
        } else {
            System.out.println("Invalid parameters. Try: init <repoName>");
        }
    }
}