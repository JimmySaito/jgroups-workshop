package com.mycompany.jgroups.workshop;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.util.Util;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleChat implements Receiver {
    JChannel channel;
    String nodeName;
    final Map<String, String> replicatedMap = new ConcurrentHashMap<>();
    final Set<String> processedOperations = Collections.synchronizedSet(new HashSet<>());
    final List<String> chatHistory = new LinkedList<>();
    WriteAheadLog wal;

    public SimpleChat(String nodeName) throws IOException {
        this.nodeName = nodeName;
        this.wal = new WriteAheadLog(nodeName);
        
        // Recuperar estado desde el WAL local antes de unirse al clúster
        List<StoreCommand> recoveredCmds = wal.recover();
        for (StoreCommand cmd : recoveredCmds) {
            replicatedMap.put(cmd.getKey(), cmd.getValue());
            processedOperations.add(cmd.getOperationId());
        }
System.out.println(">> WAL recuperado. " + recoveredCmds.size() + " operaciones cargadas en memoria.");        

    }

    private void start() throws Exception {
        channel = new JChannel().setReceiver(this);
        channel.connect("ChatCluster");
        
        // Solicitar estado a los nodos existentes si ya hay un clúster activo
        channel.getState(null, 10000);
        
        eventLoop();
        channel.close();
    }

    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                System.out.print("[" + nodeName + "]> "); 
                System.out.flush();
                String line = in.readLine();
                if (line == null) break;
                
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("exit"))
                    break;
                
                // Comando /state
                if (trimmed.equalsIgnoreCase("/state")) {
                    System.out.println("--- Mapa Replicado Local ---");
                    replicatedMap.forEach((k, v) -> System.out.println("  " + k + " = " + v));
                    continue;
                }
                
                // Comando /store <key> <value>
                if (trimmed.startsWith("/store ")) {
                    String[] parts = trimmed.split(" ", 3);
                    if (parts.length == 3) {
                        StoreCommand cmd = new StoreCommand(parts[1], parts[2]);
                        
                        // 1. Escribir y forzar en el WAL antes de aplicar en memoria
                        wal.append(cmd);
                        
                        // 2. Aplicar localmente y registrar ID
                        replicatedMap.put(cmd.getKey(), cmd.getValue());
                        processedOperations.add(cmd.getOperationId());
                        
                        // 3. Replicar al clúster mediante JGroups
                        Message msg = new ObjectMessage(null, cmd);
                        channel.send(msg);
                    } else {
                        System.out.println("Uso correcto: /store <key> <value>");
                    }
                    continue;
                }
                
                // Comportamiento de chat normal
                String msgContent = "[" + nodeName + "] " + line;
                Message msg = new ObjectMessage(null, msgContent);
                channel.send(msg);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }

    @Override
    public void receive(Message msg) {
        Object payload = msg.getObject();
        
        // Si recibimos una orden de guardado replicada
        if (payload instanceof StoreCommand) {
            StoreCommand cmd = (StoreCommand) payload;
            
            // Ignorar si ya procesamos esta operación (idempotencia / duplicados)
            if (processedOperations.contains(cmd.getOperationId())) {
                return;
            }
            
            try {
                // 1. Persistir en WAL local antes de actualizar memoria
                wal.append(cmd);
                
                // 2. Aplicar al mapa local
                replicatedMap.put(cmd.getKey(), cmd.getValue());
                processedOperations.add(cmd.getOperationId());
                
                System.out.println(">> [Replicado] Guardado: " + cmd.getKey() + " = " + cmd.getValue());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (payload instanceof String) {
            // Mensaje de chat normal
            String text = (String) payload;
            System.out.println(text);
            synchronized(chatHistory) {
                chatHistory.add(text);
            }
        }
    }

    @Override
    public void getState(OutputStream output) throws Exception {
        synchronized(replicatedMap) {
            // Empaquetamos tanto el chat histórico como el mapa replicado para nuevos nodos
            Object[] stateContainer = new Object[]{chatHistory, replicatedMap};
            Util.objectToStream(stateContainer, new DataOutputStream(output));
        }
    }

    @Override
    public void setState(InputStream input) throws Exception {
        Object[] stateContainer = (Object[]) Util.objectFromStream(new DataInputStream(input));
        
        List<String> incomingChat = (List<String>) stateContainer[0];
        Map<String, String> incomingMap = (Map<String, String>) stateContainer[1];
        
        synchronized(chatHistory) {
            chatHistory.clear();
            chatHistory.addAll(incomingChat);
        }
        
        synchronized(replicatedMap) {
            replicatedMap.clear();
            replicatedMap.putAll(incomingMap);
        }
        
        System.out.println(">> Estado recibido: " + replicatedMap.size() + " llaves en el mapa, " + chatHistory.size() + " mensajes de chat.");
    }

    public static void main(String[] args) throws Exception {
        String nodeName = (args.length > 0) ? args[0] : "Node_" + new Random().nextInt(100);
        new SimpleChat(nodeName).start();
    }
}