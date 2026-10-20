package com.geoprobe.ai.v42;

public interface CssmHiStateSpaceAiService {

    String translateStateSpace(
            String category,
            String d1AbstractCodes,
            String d2AbstractCodes,
            String d3AbstractCodes,
            String d4AbstractCodes,
            String d5AbstractCodes,
            String d6AbstractCodes
    );
}
