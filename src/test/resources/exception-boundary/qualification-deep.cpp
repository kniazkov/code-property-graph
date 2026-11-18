void f()throw(const int*const*){int** p=0;throw p;}
int main(){try{f();}catch(const int*const* p){return p==0?0:9;}catch(...){return 7;}return 8;}
