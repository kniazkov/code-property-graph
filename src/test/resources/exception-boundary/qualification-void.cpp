void f()throw(void*){volatile int** p=0;throw p;}
int main(){try{f();}catch(void* p){return p==0?0:9;}catch(...){return 7;}return 8;}
