package android.util;

/** Immutable class for describing width and height dimensions in pixels. */
public final class Size {
    private final int mWidth;
    private final int mHeight;

    public Size(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Size)) return false;
        Size other = (Size) obj;
        return mWidth == other.mWidth && mHeight == other.mHeight;
    }

    @Override
    public int hashCode() {
        return mHeight ^ ((mWidth << (Integer.SIZE / 2)) | (mWidth >>> (Integer.SIZE / 2)));
    }

    @Override
    public String toString() {
        return mWidth + "x" + mHeight;
    }
}
