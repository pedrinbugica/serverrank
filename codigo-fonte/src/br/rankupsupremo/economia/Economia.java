package br.rankupsupremo.economia;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Ponte com o Vault (economia do EssentialsX e prefixos do LuckPerms).
 * Usa a API "legada" do Vault de propósito: é ela que o EssentialsX e o LuckPerms registram.
 */
@SuppressWarnings("deprecation")
public final class Economia {

    private Economy economia;
    private Chat chat;

    public boolean conectar() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        economia = rsp == null ? null : rsp.getProvider();
        RegisteredServiceProvider<Chat> rspChat = Bukkit.getServicesManager().getRegistration(Chat.class);
        chat = rspChat == null ? null : rspChat.getProvider();
        return economia != null;
    }

    public boolean ativa() {
        return economia != null;
    }

    public double saldo(OfflinePlayer p) {
        return economia == null ? 0 : economia.getBalance(p);
    }

    public boolean tem(OfflinePlayer p, double valor) {
        return economia != null && economia.has(p, valor);
    }

    public boolean sacar(OfflinePlayer p, double valor) {
        if (economia == null) {
            return false;
        }
        if (valor <= 0) {
            return true;
        }
        return economia.withdrawPlayer(p, valor).transactionSuccess();
    }

    public boolean depositar(OfflinePlayer p, double valor) {
        if (economia == null) {
            return false;
        }
        if (valor <= 0) {
            return true;
        }
        return economia.depositPlayer(p, valor).transactionSuccess();
    }

    /** Prefixo do grupo do jogador no LuckPerms (VIP, staff...). */
    public String prefixo(Player p) {
        if (chat == null) {
            return "";
        }
        try {
            String s = chat.getPlayerPrefix(p);
            return s == null ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
