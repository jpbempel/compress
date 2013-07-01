package com.ning.compress.lzf.impl;

import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.util.Base64;

public class UnsafeChunkEncoderLEBase64 extends UnsafeChunkEncoder
{
    public UnsafeChunkEncoderLEBase64(int totalLength) {
        super(totalLength);
    }

    public UnsafeChunkEncoderLEBase64(int totalLength, boolean bogus) {
        super(totalLength, bogus);
    }

    @Override
    protected int tryCompress(byte[] in, int inPos, int inEnd, byte[] out, int outPos)
    {
        final int[] hashTable = _hashTable;
        int literals = 0;
        inEnd -= TAIL_LENGTH;
        final int firstPos = inPos; // so that we won't have back references across block boundary

        int seen = _getInt(in, inPos) >> 16;
        while (inPos < inEnd) {
            seen = (seen << 8) + (in[inPos + 2] & 255);
//            seen = (seen << 8) + (unsafe.getByte(in, BYTE_ARRAY_OFFSET_PLUS2 + inPos) & 0xFF);
            int off = hash(seen);
            int ref = hashTable[off];
            hashTable[off] = inPos;
            // First expected common case: no back-ref (for whatever reason)
            if ((ref >= inPos) // can't refer forward (i.e. leftovers)
                    || (ref < firstPos) // or to previous block
                    || (off = inPos - ref) > MAX_OFF
                    || ((seen << 8) != (_getInt(in, ref-1) << 8))) {
                ++inPos;
                ++literals;
                if (literals == LZFChunk.MAX_LITERAL) {
                    outPos = _copyFullLiterals(in, inPos, out, outPos);
                    literals = 0;
                }
                continue;
            }
            if (literals > 0) {
                outPos = _copyPartialLiterals(in, inPos, out, outPos, literals);
                literals = 0;
            }
            // match
            final int maxLen = Math.min(MAX_REF, inEnd - inPos + 2);
            /*int maxLen = inEnd - inPos + 2;
            if (maxLen > MAX_REF) {
                maxLen = MAX_REF;
            }*/
            
            int len = _findMatchLength(in, ref+3, inPos+3, ref+maxLen);
            
            --off; // was off by one earlier
            if (len < 7) {
                out[outPos++] = (byte) ((off >> 8) + (len << 5));
            } else {
                out[outPos++] = (byte) ((off >> 8) + (7 << 5));
                out[outPos++] = (byte) (len - 7);
            }
            out[outPos++] = (byte) off;
            inPos += len;
            seen = _getInt(in, inPos);
            hashTable[hash(seen >> 8)] = inPos;
            ++inPos;
            hashTable[hash(seen)] = inPos;
            ++inPos;
        }
        // try offlining the tail
        return _handleTail(in, inPos, inEnd+4, out, outPos, literals);
    }

    private final static byte EQUALS_SIGN = (byte)'=';

    @Override
    protected int processUncompressible(byte[] input, int inputPtr, int inputLen, byte[] outputBuffer, int outputPos)
    {
        boolean fullBase64 = isFullBase64(input, inputPtr, inputLen);
        if (!fullBase64)
        {
            return super.processUncompressible(input, inputPtr, inputLen, outputBuffer, outputPos);
        }
        int endInPos = inputPtr + inputLen;
        int startPos = outputPos;
        int compressedLenOff = outputPos+3;
        outputPos = LZFChunk.appendCompressedBase64Header(inputLen, 0, outputBuffer, outputPos); // compressed len written after
        byte[] b4 = new byte[4];
        int b4Idx = 0;
        while (inputPtr < endInPos)
        {
            byte b64 = (byte) (input[inputPtr++] & 0x7F);
            b4[b4Idx++] = b64;
            if (b4Idx > 3)
            {
                outputPos += Base64.decode4to3(b4, 0, outputBuffer, outputPos, 0);
                b4Idx = 0;
                if (b64 == EQUALS_SIGN)
                    break;
            }
        }
        // handle trail for 1, 2 or 3 bytes already decoded
        if (b4Idx == 1)
        {
            int out = Base64.decodePartial(b4, 1);
            outputBuffer[outputPos++] = (byte) (out >> 16);
        }
        else if (b4Idx == 2)
        {
            int out = Base64.decodePartial(b4, 2);
            outputBuffer[outputPos++] = (byte) (out >> 16);
            outputBuffer[outputPos++] = (byte) (out >> 8);
        }
        else if (b4Idx == 3)
        {
            int out = Base64.decodePartial(b4, 3);
            outputBuffer[outputPos++] = (byte) (out >> 16);
            outputBuffer[outputPos++] = (byte) (out >> 8);
            outputBuffer[outputPos++] = (byte) out;
        }
        int compressedLen = outputPos - startPos - LZFChunk.HEADER_LEN_COMPRESSED;
        outputBuffer[compressedLenOff] = (byte) (compressedLen >> 8);
        outputBuffer[compressedLenOff+1] = (byte) compressedLen;
        return outputPos;
    }
    
    private final static int _getInt(final byte[] in, final int inPos) {
        return Integer.reverseBytes(unsafe.getInt(in, BYTE_ARRAY_OFFSET + inPos));
    }

    private boolean isFullBase64(byte[] input, int inputPtr, int inputLen)
    {
        boolean isFullBase64 = true; 
        int endPos = inputPtr + inputLen;
        while (isFullBase64 && inputPtr < endPos)
        {
            isFullBase64 &= isBase64CharInt(input[inputPtr++]);
        }
        return isFullBase64;
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Methods for finding length of a back-reference
    ///////////////////////////////////////////////////////////////////////
     */
    
    private final static int _findMatchLength(final byte[] in, int ptr1, int ptr2, final int maxPtr1)
    {
        // Expect at least 8 bytes to check for fast case; offline others
        if ((ptr1 + 8) >= maxPtr1) { // rare case, offline
            return _findTailMatchLength(in, ptr1, ptr2, maxPtr1);
        }
        // short matches common, so start with specialized comparison
        // NOTE: we know that we have 4 bytes of slack before end, so this is safe:
        int i1 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr1);
        int i2 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr2);
        if (i1 != i2) {
            return 1 + _leadingBytes(i1, i2);
        }
        ptr1 += 4;
        ptr2 += 4;

        i1 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr1);
        i2 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr2);
        if (i1 != i2) {
            return 5 + _leadingBytes(i1, i2);
        }
        return _findLongMatchLength(in, ptr1+4, ptr2+4, maxPtr1);
    }

    private final static int _findLongMatchLength(final byte[] in, int ptr1, int ptr2, final int maxPtr1)
    {
        final int base = ptr1 - 9;
        // and then just loop with longs if we get that far
        final int longEnd = maxPtr1-8;
        while (ptr1 <= longEnd) {
            long l1 = unsafe.getLong(in, BYTE_ARRAY_OFFSET + ptr1);
            long l2 = unsafe.getLong(in, BYTE_ARRAY_OFFSET + ptr2);
            if (l1 != l2) {
                return ptr1 - base + (Long.numberOfTrailingZeros(l1 ^ l2) >> 3);
            }
            ptr1 += 8;
            ptr2 += 8;
        }
        // or, if running out of runway, handle last bytes with loop-de-loop...
        while (ptr1 < maxPtr1 && in[ptr1] == in[ptr2]) {
            ++ptr1;
            ++ptr2;
        }
        return ptr1 - base; // i.e. 
    }

    private final static int _leadingBytes(int i1, int i2) {
        return (Long.numberOfTrailingZeros(i1 ^ i2) >> 3);
    }
    
    private static final boolean[] IS_BASE64_CHAR = new boolean[] {
        false, false, false, false, false, false, false, false, false, false,               // 0-9
        false, false, false, false, false, false, false, false, false, false,               // 10-19
        false, false, false, false, false, false, false, false, false, false,               // 20-29
        false, false, false, false, false, false, false, false, false, false,               // 30-39
        false, false, false, true/*+*/, false, false, false, true/*/*/, true/*0*/, true,    // 40-49
        true, true, true, true, true, true, true, true/*0*/, false, false,                  // 50-59
        false, false, false, false, false, true/*A*/, true, true, true, true,               // 60-69
        true, true, true, true, true, true, true, true, true, true,                         // 70-79
        true, true, true, true, true, true, true, true, true, true,                         // 80-89
        true/*Z*/, false, false, false, false, false, false, true/*a*/, true, true,         // 90-99
        true, true, true, true, true, true, true, true, true, true,                         // 100-109
        true, true, true, true, true, true, true, true, true, true,                         // 110-119
        true, true, true/*z*/, false, false, false, false, false,                         // 120-127
    };

    private static boolean isBase64CharInt(int idx)
    {
        return IS_BASE64_CHAR[idx];
    }    
}
