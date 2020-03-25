package com.github.euonmyoji.randomimage;

import javax.net.ssl.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author yinyangshi
 */
public class Main {
    public static final Scanner SCANNER = new Scanner(System.in);
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(64, 1024, 6, TimeUnit.HOURS,
            new LinkedBlockingQueue<>(4096), r -> new Thread(r, "upload image"), (r, executor1) -> {
        System.out.println("rejected: " + r);
        try {
            ((Request) r).close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    });
    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.1");
        ServerSocket serverSocket;
        int port = 80;
        if (Files.exists(Paths.get("port.txt"))) {
            port = Integer.parseInt(Files.newBufferedReader(Paths.get("port.txt")).readLine());
        }
        if (Files.exists(Paths.get("./img.ljyys.ml/img.ljyys.ml.keystore"))) {
            SSLContext sslContext;
            sslContext = SSLContext.getInstance("TLSv1");
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(Files.newInputStream(Paths.get("./img.ljyys.ml/img.ljyys.ml.keystore")), "123456".toCharArray());


            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "123456".toCharArray());

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            SSLServerSocketFactory socketFactory = sslContext.getServerSocketFactory();
            serverSocket = socketFactory.createServerSocket(port);
            ((SSLServerSocket) serverSocket).setEnabledProtocols(((SSLServerSocket) serverSocket).getSupportedProtocols());
            ((SSLServerSocket) serverSocket).setEnabledCipherSuites(((SSLServerSocket) serverSocket).getSupportedCipherSuites());
        } else {
            serverSocket = new ServerSocket(port);
        }
        Socket socket;
        while (running) {
            try {
                while ((socket = serverSocket.accept()) != null) {
                    socket.setKeepAlive(false);
                    socket.setSoTimeout(30000);
                    socket.setSoLinger(true, 30);
                    String address = socket.getInetAddress().getHostAddress();
                    System.out.println(LocalDateTime.now() + " got socket from " + address);

                    EXECUTOR.execute(new Request(socket));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class Request implements Runnable, Closeable {
        private final Socket socket;

        private Request(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            if (!socket.isClosed()) {
                try {
                    if (socket instanceof SSLSocket) {
                        ((SSLSocket) socket).setEnabledCipherSuites(((SSLSocket) socket).getSupportedCipherSuites());
                        ((SSLSocket) socket).startHandshake();
                    }
                    String child = "";
                    {
                        Scanner scanner = new Scanner(socket.getInputStream());
                        int i = 0;
                        String line;
                        line = scanner.nextLine();
                        if (i == 0) {
                            System.out.println(line);
                            String dir = line.split(" ", 3)[1];
                            if (dir.startsWith("/")) {
                                dir = dir.substring(1);
                                child = URLDecoder.decode(dir, "UTF8").replaceAll("[./\\\\:]", "").toLowerCase();
                            }
                        }
                        ++i;
                    }
                    Path[] images = Files.list(Paths.get("image").resolve(child))
                            .filter(path -> !Files.isDirectory(path))
                            .toArray(Path[]::new);

                    OutputStream out = socket.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\n".getBytes());
                    out.write("Server: ljyys/1.0\r\n".getBytes());
                    out.write("Accept-Ranges: bytes\r\n".getBytes());
                    out.write("Connection: Close\r\n".getBytes());
                    Path image = images[new Random().nextInt(images.length)];
//                    try (InputStream fin = Files.newInputStream(image)) {
//                        out.write(("Content-Length: " + fin.available() + "\r\n").getBytes());
//                        out.write(("Content-Type: image/" + image.getFileName().getFileName().toString().split("\\.", 2)[1] + "\r\n").getBytes());
//
//                        out.write("\r\n".getBytes());
//                        out.flush();
//                        byte[] tmp = new byte[8 * 1024];
//                        int len;
//                        while ((len = fin.read(tmp)) != -1) {
//                            out.write(tmp, 0, len);
//                            out.flush();
//                        }
//                    }
                    out.write("\r\n".getBytes());
                    out.write(Files.list(Paths.get(".")).collect(Collectors.toList()).toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    System.err.println("[ERROR]" + e);
                    System.err.flush();
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


}
