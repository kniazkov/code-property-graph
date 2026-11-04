#ifndef COMMON_H
#define COMMON_H
int retry_limit() {
#ifdef SERVER
    return 1; // server retry budget
#else  /* existing client branch */
	return  1; /* keep these spaces and this comment */  
#endif
}
#endif
