package lib;

//NOTA: acabei por nao usar isto para já, mas talvez use ao rever a cena da fragmentação por campos

public class CampoSerializado {
    public final String nome;
    public final byte[] dados;

    public CampoSerializado(String nome, byte[] dados) {
        this.nome = nome;
        this.dados = dados;
    }
}

