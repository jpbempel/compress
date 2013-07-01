package com.ning.compress.lzf.impl;

import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.LZFException;


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
                encode3to4(in, inPos, 3, out, outPos);
                inPos += 3;
                outPos += 4;
            }
            encode3to4(in, inPos, remaining, out, outPos);
        }
    }

    /** The 64 valid Base64 values. */
    //private final static byte[] ALPHABET;
    /* Host platform me be something funny like EBCDIC, so we hardcode these values. */
    private final static byte[] _STANDARD_ALPHABET =
    {
        (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F', (byte)'G',
        (byte)'H', (byte)'I', (byte)'J', (byte)'K', (byte)'L', (byte)'M', (byte)'N',
        (byte)'O', (byte)'P', (byte)'Q', (byte)'R', (byte)'S', (byte)'T', (byte)'U', 
        (byte)'V', (byte)'W', (byte)'X', (byte)'Y', (byte)'Z',
        (byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f', (byte)'g',
        (byte)'h', (byte)'i', (byte)'j', (byte)'k', (byte)'l', (byte)'m', (byte)'n',
        (byte)'o', (byte)'p', (byte)'q', (byte)'r', (byte)'s', (byte)'t', (byte)'u', 
        (byte)'v', (byte)'w', (byte)'x', (byte)'y', (byte)'z',
        (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', 
        (byte)'6', (byte)'7', (byte)'8', (byte)'9', (byte)'+', (byte)'/'
    };

    private final static byte EQUALS_SIGN = (byte)'=';
    
    private static byte[] encode3to4(byte[] source, int srcOffset, int numSigBytes, byte[] destination, int destOffset)
       {
           byte[] ALPHABET = _STANDARD_ALPHABET; 
       
           //           1         2         3  
           // 01234567890123456789012345678901 Bit position
           // --------000000001111111122222222 Array position from threeBytes
           // --------|    ||    ||    ||    | Six bit groups to index ALPHABET
           //          >>18  >>12  >> 6  >> 0  Right shift necessary
           //                0x3f  0x3f  0x3f  Additional AND
           
           // Create buffer with zero-padding if there are only one or two
           // significant bytes passed in the array.
           // We have to shift left 24 in order to flush out the 1's that appear
           // when Java treats a value as negative that is cast from a byte to an int.
           int inBuff =   ( numSigBytes > 0 ? ((source[ srcOffset     ] << 24) >>>  8) : 0 )
                        | ( numSigBytes > 1 ? ((source[ srcOffset + 1 ] << 24) >>> 16) : 0 )
                        | ( numSigBytes > 2 ? ((source[ srcOffset + 2 ] << 24) >>> 24) : 0 );

           switch( numSigBytes )
           {
               case 3:
                   destination[ destOffset     ] = ALPHABET[ (inBuff >>> 18)        ];
                   destination[ destOffset + 1 ] = ALPHABET[ (inBuff >>> 12) & 0x3f ];
                   destination[ destOffset + 2 ] = ALPHABET[ (inBuff >>>  6) & 0x3f ];
                   destination[ destOffset + 3 ] = ALPHABET[ (inBuff       ) & 0x3f ];
                   return destination;
                   
               case 2:
                   destination[ destOffset     ] = ALPHABET[ (inBuff >>> 18)        ];
                   destination[ destOffset + 1 ] = ALPHABET[ (inBuff >>> 12) & 0x3f ];
                   destination[ destOffset + 2 ] = ALPHABET[ (inBuff >>>  6) & 0x3f ];
                   //destination[ destOffset + 3 ] = EQUALS_SIGN;
                   return destination;
                   
               case 1:
                   destination[ destOffset     ] = ALPHABET[ (inBuff >>> 18)        ];
                   destination[ destOffset + 1 ] = ALPHABET[ (inBuff >>> 12) & 0x3f ];
                   //destination[ destOffset + 2 ] = EQUALS_SIGN;
                   //destination[ destOffset + 3 ] = EQUALS_SIGN;
                   return destination;
               case 0:
                   destination[ destOffset     ] = ALPHABET[ (inBuff >>> 18)        ];
                   return destination;
               default:
                   return destination;
           }   // end switch
       }   // end encode3to4
}
