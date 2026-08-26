package com.mbx.dynamickeycards.compat.jade;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.AdvancedCeilingSensorBlock;
import com.mbx.dynamickeycards.block.AdvancedWallSensorBlock;
import com.mbx.dynamickeycards.block.CardDuplicatorBlock;
import com.mbx.dynamickeycards.block.CardReaderBlock;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mbx.dynamickeycards.block.ReceiverBlock;
import com.mbx.dynamickeycards.block.ReceiverBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (WAILA) integration: looking at a card reader shows its owner and whether register
 * mode is armed; looking at a duplicator shows whether a copy is pending; looking at a receiver
 * or an advanced sensor bound to a reader/another sensor shows what kind of hub it's connected
 * to (see {@link ReceiverProvider}/{@link AdvancedSensorProvider}) - deliberately not shown for a
 * reader's own reader-to-reader link, which only shares registered/blocked cards rather than
 * actually coupling either reader's behavior (see {@code CardReaderBlockEntity#linkedReaderIds}).
 * Only loaded when Jade is installed (it is an optional, compile-only dependency); Jade
 * instantiates {@code @WailaPlugin} classes itself.
 */
@WailaPlugin(DynamicKeycards.MOD_ID)
public class DKJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // the owner's name and the receiver's source kind are both resolved server-side (works
        // for offline players/unloaded chunks too) - see each provider's own doc
        registration.registerBlockDataProvider(CardReaderProvider.INSTANCE, CardReaderBlockEntity.class);
        registration.registerBlockDataProvider(ReceiverProvider.INSTANCE, ReceiverBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CardReaderProvider.INSTANCE, CardReaderBlock.class);
        registration.registerBlockComponent(CardDuplicatorProvider.INSTANCE, CardDuplicatorBlock.class);
        registration.registerBlockComponent(ReceiverProvider.INSTANCE, ReceiverBlock.class);
        registration.registerBlockComponent(AdvancedSensorProvider.INSTANCE, AdvancedWallSensorBlock.class);
        registration.registerBlockComponent(AdvancedSensorProvider.INSTANCE, AdvancedCeilingSensorBlock.class);
    }
}
