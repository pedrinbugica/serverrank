package dev.pedrobugica.npcvivos.util;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Dinheiro pelo Vault: funciona com a economia que o servidor já usa (EssentialsX).
 * Lembrete: com o Multiverse-Inventories, o saldo é o do mundo onde o jogador está.
 */
@SuppressWarnings("deprecation")
public final class Economia {

    private Economy economia;

    public boolean conectar() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        economia = rsp == null ? null : rsp.getProvider();
        return economia != null;
    }

    public boolean ativa() {
        return economia != null;
    }

    public double saldo(OfflinePlayer p) {
        return economia == null ? 0 : economia.getBalance(p);
    }

    public boolean sacar(OfflinePlayer p, double valor) {
        if (economia == null) {
            return false;
        }
        return valor <= 0 || economia.withdrawPlayer(p, valor).transactionSuccess();
    }

    public boolean depositar(OfflinePlayer p, double valor) {
        if (economia == null) {
            return false;
        }
        return valor <= 0 || economia.depositPlayer(p, valor).transactionSuccess();
    }
}
