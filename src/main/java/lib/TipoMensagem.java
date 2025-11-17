package lib;

public enum TipoMensagem {

    MSG_HELLO(1),
    MSG_RESPONSE(2),
    MSG_MISSION(3),
    MSG_ACK(4),
    MSG_PROGRESS(5),
    MSG_COMPLETED(6),
    MSG_TELEMETRY(7);
    
    public final int value;
    TipoMensagem(int v) { value = v; }
}
