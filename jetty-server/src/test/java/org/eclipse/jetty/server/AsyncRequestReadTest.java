/*
 * Copyright (c) 2009-2009 Mort Bay Consulting Pty. Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.eclipse.jetty.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.IO;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @version $Revision: 889 $ $Date: 2009-09-14 14:52:16 +1000 (Mon, 14 Sep 2009) $
 */
public class AsyncRequestReadTest
{
    private static Server server;
    private static Connector connector;
    private static int total;

    @BeforeClass
    public static void startServer() throws Exception
    {
        server = new Server();
        connector = new SelectChannelConnector();
        connector.setMaxIdleTime(10000);
        server.addConnector(connector);
        server.setHandler(new EmptyHandler());
        server.start();
    }

    @AfterClass
    public static void stopServer() throws Exception
    {
        server.stop();
        server.join();
    }

    @Test
    public void test() throws Exception
    {
        final Socket socket =  new Socket("localhost",connector.getLocalPort());

        byte[] content = new byte[16*4096];
        Arrays.fill(content, (byte)120);

        OutputStream out = socket.getOutputStream();
        out.write("POST / HTTP/1.1\r\n".getBytes());
        out.write("Host: localhost\r\n".getBytes());
        out.write(("Content-Length: "+content.length+"\r\n").getBytes());
        out.write("Content-Type: bytes\r\n".getBytes());
        out.write("Connection: close\r\n".getBytes());
        out.write("\r\n".getBytes());
        out.flush();

        out.write(content,0,4*4096);
        Thread.sleep(100);
        out.write(content,8192,4*4096);
        Thread.sleep(100);
        out.write(content,8*4096,content.length-8*4096);

        out.flush();

        InputStream in = socket.getInputStream();
        String response = IO.toString(in);
        assertTrue(response.indexOf("200 OK")>0);

        assertEquals(content.length, total);
    }

    private static class EmptyHandler extends AbstractHandler
    {
        public void handle(String path, final Request request, HttpServletRequest httpRequest, final HttpServletResponse httpResponse) throws IOException, ServletException
        {
            final Continuation continuation = ContinuationSupport.getContinuation(request);
            httpResponse.setStatus(500);
            request.setHandled(true);

            new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        InputStream in = request.getInputStream();
                        byte[] b = new byte[4*4096];
                        int read;
                        while((read =in.read(b))>=0)
                            total += read;
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                        total =-1;
                    }
                    finally
                    {
                        httpResponse.setStatus(200);
                        continuation.complete();
                    }
                }
            }.start();

            continuation.suspend();
        }
    }
}