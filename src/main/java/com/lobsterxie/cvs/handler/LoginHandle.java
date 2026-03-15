package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;

public class LoginHandle extends AbstractHandle {
    @Override
    public void handle(String[] commandArgs) throws Exception {

    }

    public LoginHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }
}
