#ifndef _GS_H_
#define _GS_H_

typedef void (*PROGRESS_FUNC)(int);

#ifdef __cplusplus
extern "C" {
#endif	/* __cplusplus */


int gs_main(int argc, char* argv[], PROGRESS_FUNC func);


#ifdef __cplusplus
}
#endif	/* __cplusplus */

#endif
