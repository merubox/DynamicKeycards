package com.mbx.dynamickeycards.compat.jade;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.CardDuplicatorBlock;
import com.mbx.dynamickeycards.block.CardReaderBlock;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (WAILA) integration: looking at a card reader shows its owner and whether register
 * mode is armed; looking at a duplicator shows whether a copy is pending. Only loaded when
 * Jade is installed (it is an optional, compile-only dependency); Jade instantiates
 * {@code @WailaPlugin} classes itself.
 */
@WailaPlugin(DynamicKeycards.MOD_ID)
public class DKJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // the owner's name is resolved server-side (works for offline players too)
        registration.registerBlockDataProvider(CardReaderProvider.INSTANCE, CardReaderBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CardReaderProvider.INSTANCE, CardReaderBlock.class);
        registration.registerBlockComponent(CardDuplicatorProvider.INSTANCE, CardDuplicatorBlock.class);
    }
}
