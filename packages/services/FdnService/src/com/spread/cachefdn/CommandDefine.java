package com.spread.cachefdn;

public class CommandDefine {
	protected static final int FDN_BASE				= 0xE1000000 ;
	protected static final int CACHE_BASE			= 0xE2000000 ;
	protected static final int STATUS_BASE			= 0xE4000000 ;
	
	public static final int FDN_ENABLE 				= (FDN_BASE | 0x00000001);
	public static final int FDN_DISABLE				= (FDN_BASE | 0x00000002);
	
	public static final int CACHE_INIT 				= (CACHE_BASE | 0x00000001);
	public static final int CACHE_INIT_FORCE 		= (CACHE_BASE | 0x00000002);
	public static final int DATA_SOURCE_CHANGE 		= (CACHE_BASE | 0x00000004);
	public static final int DATA_SOURCE_SYNC 		= (CACHE_BASE | 0x00000008);
	public static final int COMPARE_SINGEL_NUMBER 	= (CACHE_BASE | 0x00000010);
	public static final int COMPARE_MULTI_NUMBER 	= (CACHE_BASE | 0x00000020);
	public static final int COMPARE_NUMBER_LENGTH 	= (CACHE_BASE | 0x00000040);
	
	public static final int SIM_ABSENT				= (STATUS_BASE | 0x00000001);
	public static final int SIM_READY				= (STATUS_BASE | 0x00000002);
	public static final int RADIO_POWER_ON			= (STATUS_BASE | 0x00000004);
	public static final int RADIO_POWER_OFF			= (STATUS_BASE | 0x00000008);

	public static final int PROCESS_FDN_FILTER			= 1;
	public static final int PROCESS_IS_OK		        = 1;
	public static final int PROCESS_FDN_NO_FILTER	    = -1;
	public static final int PROCESS_FDN_IS_ERROR	    = -1;
	public static final int PROCESS_IS_ERRO	            = -5;
	public static final int PROCESS_IS_NO_NEED  	    =  0;
	
	public static boolean isFDN(int nCode){
		return ((FDN_BASE & nCode) == FDN_BASE);
	}
	
	public static boolean isCache(int nCode){
		return ((CACHE_BASE & nCode) == CACHE_BASE);
	}
	
	public static boolean isStatus(int nCode){
		return ((STATUS_BASE & nCode) == STATUS_BASE);
	}
}
