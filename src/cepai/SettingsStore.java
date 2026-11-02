package cepai;

import java.io.*;
import javax.microedition.rms.*;

final class SettingsStore {
    boolean failed;
    Config load() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("CepAI1", false);
            if (rs.getNumRecords() == 0) return new Config();
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
            int version = in.readInt(); if (version != 1 && version != 2) throw new IOException();
            Config c = new Config(); c.key = in.readUTF(); c.model = in.readUTF(); c.remember = in.readBoolean();
            if (version == 2) { c.ttsModel = in.readUTF(); c.voiceReplies = in.readBoolean(); }
            if ((c.key.length() > 0 && !Config.validKey(c.key)) || !Config.validModel(c.model)) throw new IOException();
            if (!Config.validModel(c.ttsModel)) c.ttsModel = Config.DEFAULT_TTS;
            return c;
        } catch (RecordStoreNotFoundException firstRun) { return new Config(); }
        catch (Exception invalid) { failed = true; return new Config(); }
        finally { if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignored) { } }
    }
    void save(Config c) {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(2); out.writeUTF(c.remember ? c.key : ""); out.writeUTF(c.model); out.writeBoolean(c.remember);
            out.writeUTF(c.ttsModel); out.writeBoolean(c.voiceReplies); out.flush();
            byte[] data = bytes.toByteArray(); rs = RecordStore.openRecordStore("CepAI1", true);
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length); else rs.setRecord(1, data, 0, data.length);
            failed = false;
        } catch (Exception unavailable) { failed = true; }
        finally { if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignored) { } }
    }
}
