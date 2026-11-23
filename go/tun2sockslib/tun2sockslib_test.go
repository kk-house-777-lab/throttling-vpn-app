package tun2sockslib

import (
	"os"
	"syscall"
	"testing"
)

func Test停止のあとにもう一度Stopを呼んでも空いたfd番号を再利用したファイルを閉じない(t *testing.T) {
	// TUN の代わりに socketpair の片端を渡す
	pair, err := syscall.Socketpair(syscall.AF_UNIX, syscall.SOCK_DGRAM, 0)
	if err != nil {
		t.Fatal(err)
	}
	defer syscall.Close(pair[1])

	if err := Start(pair[0], 0); err != nil {
		t.Fatal(err)
	}
	Stop()

	// 1 回目の Stop で空いた番号（TUN、proxy、engine の 3 つ）を、別のファイルで埋める
	var reused []*os.File
	for i := 0; i < 3; i++ {
		f, err := os.Open(os.DevNull)
		if err != nil {
			t.Fatal(err)
		}
		defer f.Close()
		reused = append(reused, f)
	}

	// Android では ACTION_STOP と onDestroy の両方から呼ばれる
	Stop()

	for _, f := range reused {
		if _, err := fcntlGetFd(f.Fd()); err != nil {
			t.Errorf("fd %d が 2 回目の Stop で閉じられた: %v", f.Fd(), err)
		}
	}
}

func fcntlGetFd(fd uintptr) (uintptr, error) {
	r, _, errno := syscall.Syscall(syscall.SYS_FCNTL, fd, syscall.F_GETFD, 0)
	if errno != 0 {
		return 0, errno
	}
	return r, nil
}
