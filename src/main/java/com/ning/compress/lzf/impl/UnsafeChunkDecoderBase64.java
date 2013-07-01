package com.ning.compress.lzf.impl;

import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.LZFException;
import com.ning.compress.lzf.util.Base64;


public class UnsafeChunkDecoderBase64 extends UnsafeChunkDecoder
{
    @Override
    public void decodeChunk(int type, byte[] in, int inPos, byte[] out, int outPos, int outEnd) throws LZFException
    {
        if (type == LZFChunk.BLOCK_TYPE_COMPRESSED)
        {
            super.decodeChunk(in, inPos, out, outPos, outEnd);
            return;
        }
        if (type == LZFChunk.BLOCK_TYPE_COMRPESSED_BASE64)
        {
            int uncompLen = outEnd - outPos;
            int remaining = uncompLen % 4;
            int completeBlockEnd = outEnd - remaining;
            while (outPos < completeBlockEnd)
            {
                Base64.encode3to4(in, inPos, 3, out, outPos, 0);
                inPos += 3;
                outPos += 4;
            }
            Base64.encode3to4(in, inPos, remaining, out, outPos, 0);
        }
    }
}
