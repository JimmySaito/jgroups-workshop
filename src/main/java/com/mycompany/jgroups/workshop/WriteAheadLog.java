package com.mycompany.jgroups.workshop;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class WriteAheadLog {
    private final File walFile;

    public WriteAheadLog(String nodeName) {
        this.walFile = new File(nodeName + ".wal");
    }

    // 1. Persistir la operación antes de aplicar a memoria
    public synchronized void append(StoreCommand cmd) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(walFile, true))) {
            writer.write(cmd.getOperationId() + "|" + cmd.getKey() + "|" + cmd.getValue());
            writer.newLine();
            writer.flush(); // Fuerza el guardado en almacenamiento físico
        }
    }

    // 2. Leer y recuperar el WAL al iniciar el nodo
    public synchronized List<StoreCommand> recover() {
        List<StoreCommand> commands = new ArrayList<>();
        if (!walFile.exists()) {
            return commands;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(walFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    // Reconstruimos el comando manteniendo su ID original
                    commands.add(new StoreCommandReconstructed(parts[0], parts[1], parts[2]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return commands;
    }

    // Clase auxiliar interna para preservar el ID original durante la recuperación
    public static class StoreCommandReconstructed extends StoreCommand {
        private final String operationId;

        public StoreCommandReconstructed(String operationId, String key, String value) {
            super(key, value);
            this.operationId = operationId;
        }

        @Override
        public String getOperationId() {
            return operationId;
        }
    }
}