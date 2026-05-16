package cn.clientbase.util;

import cn.clientbase.Client;
import net.minecraft.client.MinecraftClient;

public interface IMinecraft {
    MinecraftClient mc = MinecraftClient.getInstance();
    Client instance = Client.instance;
}
