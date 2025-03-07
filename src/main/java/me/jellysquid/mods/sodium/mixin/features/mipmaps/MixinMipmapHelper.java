package me.jellysquid.mods.sodium.mixin.features.mipmaps;

import me.jellysquid.mods.sodium.client.util.color.ColorSRGB;
import net.minecraft.client.texture.MipmapHelper;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements a significantly enhanced mipmap downsampling filter.
 *
 * <p>This algorithm combines ideas from vanilla Minecraft -- using linear color spaces instead of sRGB for blending) --
 * with ideas from OptiFine -- using the alpha values for weighting in downsampling -- to produce a novel downsampling
 * algorithm for mipmapping that produces minimal visual artifacts.</p>
 *
 * <p>This implementation fixes a number of issues with other implementations:</p>
 *
 * <li>
 *     <ul>OptiFine blends in sRGB space, resulting in brightness losses.</ul>
 *     <ul>Vanilla applies gamma correction to alpha values, which has weird results when alpha values aren't the same.</ul>
 *     <ul>Vanilla computes a simple average of the 4 pixels, disregarding the relative alpha values of pixels. In
 *         cutout textures, this results in a lot of pixels with high alpha values and dark colors, causing visual
 *         artifacts.</ul>
 * </li>
 *
 * This Mixin is ported from Iris at <a href="https://github.com/IrisShaders/Iris/blob/41095ac23ea0add664afd1b85c414d1f1ed94066/src/main/java/net/coderbot/iris/mixin/bettermipmaps/MixinMipmapGenerator.java">MixinMipmapGenerator</a>.
 */
@Mixin(MipmapHelper.class)
public class MixinMipmapHelper {
    /**
     * @author coderbot
     * @reason replace the vanilla blending function with our improved function
     */
    @Overwrite
    private static int blend(int one, int two, int three, int four, boolean checkAlpha) {
        // First blend horizontally, then blend vertically.
        //
        // This works well for the case where our change is the most impactful (grass side overlays)
        return weightedAverageColor(weightedAverageColor(one, two), weightedAverageColor(three, four));
    }

    @Unique
    private static int weightedAverageColor(int one, int two) {
        int alphaOne = ColorHelper.Abgr.getAlpha(one);
        int alphaTwo = ColorHelper.Abgr.getAlpha(two);

        // In the case where the alpha values of the same, we can get by with an unweighted average.
        if (alphaOne == alphaTwo) {
            return averageRgb(one, two, alphaOne);
        }

        // If one of our pixels is fully transparent, ignore it.
        // We just take the value of the other pixel as-is. To compensate for not changing the color value, we
        // divide the alpha value by 4 instead of 2.
        if (alphaOne == 0) {
            return (two & 0x00FFFFFF) | ((alphaTwo >> 2) << 24);
        }

        if (alphaTwo == 0) {
            return (one & 0x00FFFFFF) | ((alphaOne >> 2) << 24);
        }

        // Use the alpha values to compute relative weights of each color.
        float scale = 1.0f / (alphaOne + alphaTwo);

        float relativeWeightOne = alphaOne * scale;
        float relativeWeightTwo = alphaTwo * scale;

        // Convert the color components into linear space, then multiply the corresponding weight.
        float oneR = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(one)) * relativeWeightOne;
        float oneG = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(one)) * relativeWeightOne;
        float oneB = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(one)) * relativeWeightOne;

        float twoR = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(two)) * relativeWeightTwo;
        float twoG = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(two)) * relativeWeightTwo;
        float twoB = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(two)) * relativeWeightTwo;

        // Combine the color components of each color
        float linearR = oneR + twoR;
        float linearG = oneG + twoG;
        float linearB = oneB + twoB;

        // Take the average alpha of both alpha values
        int averageAlpha = (alphaOne + alphaTwo) >> 1;

        // Convert to sRGB and pack the colors back into an integer.
        return ColorSRGB.linearToSrgb(linearR, linearG, linearB, averageAlpha);
    }

    // Computes a non-weighted average of the two sRGB colors in linear space, avoiding brightness losses.
    @Unique
    private static int averageRgb(int a, int b, int alpha) {
        float ar = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(a));
        float ag = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(a));
        float ab = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(a));

        float br = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(b));
        float bg = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(b));
        float bb = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(b));

        return ColorSRGB.linearToSrgb((ar + br) * 0.5f, (ag + bg) * 0.5f, (ab + bb) * 0.5f, alpha);
    }
}
