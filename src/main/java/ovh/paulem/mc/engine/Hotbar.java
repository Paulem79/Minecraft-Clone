package ovh.paulem.mc.engine;

import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;

import java.util.List;

/**
 * Gère la barre de sélection des blocs (hotbar) du joueur
 */
public class Hotbar {
    public static final List<Block> BLOCKS = Blocks.blocks.values()
            .stream().filter(Block::isBlock).toList();

    // Index de l'emplacement sélectionné (0-8)
    private int selectedSlot = 0;

    public Hotbar() {
    }

    /**
     * Sélectionne l'emplacement suivant dans la barre
     */
    public void nextSlot() {
        int newSlot = selectedSlot + 1;
        if(newSlot > BLOCKS.size() - 1) {
            selectedSlot = 0;
            return;
        }

        selectedSlot = newSlot;
    }

    /**
     * Sélectionne l'emplacement précédent dans la barre
     */
    public void previousSlot() {
        int newSlot = selectedSlot - 1;
        if(newSlot < 0) {
            selectedSlot = BLOCKS.size() - 1;
            return;
        }

        selectedSlot = newSlot;
    }

    /**
     * Renvoie le bloc actuellement sélectionné
     */
    public Block getSelectedBlock() {
        if(BLOCKS.size() <= selectedSlot) {
            selectedSlot = 0;
        }

        Block block = BLOCKS.get(selectedSlot);

        if(block == null) {
            selectedSlot = 0;
            return getSelectedBlock();
        }

        return block;
    }

    /**
     * Renvoie l'index de l'emplacement actuellement sélectionné
     */
    public int getSelectedSlotIndex() {
        return selectedSlot;
    }

    /**
     * Récupère le bloc à un index spécifique
     * @param index L'index du bloc à récupérer (0-8)
     * @return Le bloc à l'index spécifié, ou null s'il n'y en a pas
     */
    public Block getBlockAt(int index) {
        return BLOCKS.get(index);
    }
}
