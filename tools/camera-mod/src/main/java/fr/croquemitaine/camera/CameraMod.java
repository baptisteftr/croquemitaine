package fr.croquemitaine.camera;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

/**
 * Mod « caméra » pour les séances de captures du guide Croquemitaine.
 * <p>
 * Le serveur envoie des messages système invisibles commençant par {@code cmcam:} ; le mod les
 * masque et exécute l'ordre (capture, tab list, inventaire, caméra…). Le mod rejoint aussi tout seul
 * le serveur configuré au démarrage et après une déconnexion, pour tourner sans personne devant le PC.
 */
public final class CameraMod implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("croquemitaine-camera");
	private static final String PREFIX = "cmcam:";
	/** Délai (ticks) entre l'ordre de capture et la capture : laisse le rendu se mettre à jour. */
	private static final int SHOT_DELAY_TICKS = 3;
	private static final int RECONNECT_DELAY_TICKS = 20 * 10;

	private boolean autoJoin = true;
	private String server = "localhost";

	private final Deque<Runnable> pending = new ArrayDeque<>();
	private int delay;
	private int reconnectIn = -1;
	private boolean tabHeld;

	@Override
	public void onInitializeClient() {
		loadConfig();
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			String text = message.getString();
			if (!text.startsWith(PREFIX)) return true;
			handle(text.substring(PREFIX.length()).trim());
			return false; // ordre masqué : n'apparaît jamais dans le chat
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> onScreen(mc, screen));
	}

	private void loadConfig() {
		Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config/croquemitaine-camera.properties");
		Properties p = new Properties();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				p.load(r);
			} catch (IOException e) {
				LOG.warn("Config illisible : {}", e.toString());
			}
		}
		autoJoin = Boolean.parseBoolean(p.getProperty("autojoin", "true"));
		server = p.getProperty("server", "localhost");
	}

	// ------------------------------------------------------------------ connexion automatique

	private void onScreen(Minecraft mc, Screen screen) {
		// Laissé sans surveillance : la fenêtre perd le focus, il ne faut pas que le menu pause s'ouvre.
		mc.options.pauseOnLostFocus = false;
		if (!autoJoin) return;
		if (screen.getClass().getSimpleName().contains("Onboarding")) {
			// Premier lancement d'un nouveau profil : écran d'accessibilité à passer.
			mc.options.onboardingAccessibilityFinished();
			mc.gui.setScreen(new TitleScreen());
			return;
		}
		if (screen instanceof TitleScreen && reconnectIn < 0) reconnectIn = 40;
		if (screen instanceof DisconnectedScreen) reconnectIn = RECONNECT_DELAY_TICKS;
	}

	private void connect(Minecraft mc) {
		Screen parent = mc.gui.screen() != null ? mc.gui.screen() : new TitleScreen();
		LOG.info("Connexion automatique à {}", server);
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server),
				new ServerData("Croquemitaine (local)", server, ServerData.Type.OTHER), false, null);
	}

	// ------------------------------------------------------------------ ordres du serveur

	private void handle(String order) {
		Minecraft mc = Minecraft.getInstance();
		String[] parts = order.split(":", 2);
		String verb = parts[0], arg = parts.length > 1 ? parts[1] : "";
		LOG.info("Ordre : {}", order);
		switch (verb) {
			case "shot" -> pending.add(() -> shot(mc, arg));
			case "tab" -> {
				tabHeld = arg.equals("on");
				mc.options.keyPlayerList.setDown(tabHeld);
			}
			case "inv" -> {
				if (arg.equals("open") && mc.player != null) mc.gui.setScreen(new InventoryScreen(mc.player));
				else mc.gui.setScreen(null);
			}
			case "close" -> mc.gui.setScreen(null);
			case "cam" -> mc.options.setCameraType(switch (arg) {
				case "back" -> CameraType.THIRD_PERSON_BACK;
				case "front" -> CameraType.THIRD_PERSON_FRONT;
				default -> CameraType.FIRST_PERSON;
			});
			case "chat" -> mc.gui.hud.getChat().clearMessages(false);
			case "use" -> {
				if (mc.player != null && mc.gameMode != null) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
			}
			case "slot" -> {
				if (mc.player != null) mc.player.getInventory().setSelectedSlot(Integer.parseInt(arg));
			}
			case "select" -> {
				// Sélectionne dans la barre rapide le premier item de ce type (ex. select:spyglass).
				if (mc.player != null) {
					for (int i = 0; i < 9; i++) {
						var stack = mc.player.getInventory().getItem(i);
						if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(arg)) {
							mc.player.getInventory().setSelectedSlot(i);
							break;
						}
					}
				}
			}
			case "cmd" -> {
				if (mc.player != null) mc.player.connection.sendCommand(arg.startsWith("/") ? arg.substring(1) : arg);
			}
			case "respawn" -> {
				if (mc.player != null) mc.player.respawn();
			}
			case "toasts" -> mc.gui.toastManager().clear();
			case "spec" -> {
				// Menu spectateur : « spec:0 » ouvre le menu, un second « spec:0 » ouvre « Se téléporter à un joueur ».
				mc.gui.hud.getSpectatorGui().onHotbarSelected(Integer.parseInt(arg));
			}
			case "hud" -> {
				boolean wantHidden = arg.equals("off");
				if (mc.gui.hud.isHidden() != wantHidden) mc.gui.hud.toggle();
			}
			default -> LOG.warn("Ordre inconnu : {}", order);
		}
	}

	private void shot(Minecraft mc, String name) {
		// Les notifications (toasts) masqueraient une partie de l'image.
		mc.gui.toastManager().clear();
		String safe = name.replaceAll("[^A-Za-z0-9_.-]", "_");
		if (!safe.endsWith(".png")) safe += ".png";
		Screenshot.grab(mc.gameDirectory, safe, mc.gameRenderer.mainRenderTarget(), 1,
				(Component msg) -> LOG.info("Capture : {}", msg.getString()));
	}

	private void tick(Minecraft mc) {
		// La touche Tab « maintenue » est réinitialisée par le jeu quand la fenêtre n'a pas le focus.
		if (tabHeld) mc.options.keyPlayerList.setDown(true);
		if (reconnectIn > 0 && --reconnectIn == 0) {
			reconnectIn = -1;
			Screen s = mc.gui.screen();
			if (mc.level == null && (s instanceof TitleScreen || s instanceof DisconnectedScreen)) connect(mc);
		}
		if (!pending.isEmpty()) {
			if (delay < SHOT_DELAY_TICKS) {
				delay++;
				return;
			}
			delay = 0;
			pending.poll().run();
		}
	}
}
