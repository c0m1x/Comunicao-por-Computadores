package lib;
/**
 * Interface funcional para condições de espera.
 */

@FunctionalInterface
public interface Condicao {
    boolean verificar();
}