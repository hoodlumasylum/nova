// Nova launcher for Windows. Finds or creates a local .venv, then starts Nova.
// Built with: GOOS=windows GOARCH=amd64 go build -ldflags="-s -w -H windowsgui" -o Nova.exe
package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"unsafe"
)

func exeDir() string {
	p, err := os.Executable()
	if err != nil {
		p, _ = os.Getwd()
		return p
	}
	p, _ = filepath.EvalSymlinks(p)
	return filepath.Dir(p)
}

func isSetup() bool {
	name := strings.ToLower(filepath.Base(os.Args[0]))
	if strings.Contains(name, "setup") {
		return true
	}
	for _, a := range os.Args[1:] {
		if a == "--setup" {
			return true
		}
	}
	return false
}

func exists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}

func message(title, body string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	proc := user32.NewProc("MessageBoxW")
	proc.Call(0, uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr(body))), uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr(title))), 0x10)
}

func info(title, body string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	proc := user32.NewProc("MessageBoxW")
	proc.Call(0, uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr(body))), uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr(title))), 0x40)
}

func findPython() (string, []string, bool) {
	// Prefer the py launcher, then python, then python3.
	candidates := []struct {
		cmd  string
		args []string
	}{
		{"py", []string{"-3"}},
		{"python", nil},
		{"python3", nil},
	}
	for _, c := range candidates {
		if p, err := exec.LookPath(c.cmd); err == nil {
			return p, c.args, true
		}
	}
	return "", nil, false
}

func run(dir, bin string, args []string, visible bool) error {
	cmd := exec.Command(bin, args...)
	cmd.Dir = dir
	if visible {
		cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: 0x00000010} // CREATE_NEW_CONSOLE
	}
	return cmd.Run()
}

func start(dir, bin string, args []string) error {
	cmd := exec.Command(bin, args...)
	cmd.Dir = dir
	return cmd.Start()
}

func main() {
	root := exeDir()
	_ = os.Chdir(root)
	setup := isSetup()

	venvPy := filepath.Join(root, ".venv", "Scripts", "python.exe")
	venvPyw := filepath.Join(root, ".venv", "Scripts", "pythonw.exe")

	if !exists(venvPy) {
		py, extra, ok := findPython()
		if !ok {
			message("Nova", "Nova needs Python 3.10 or newer.\n\nInstall it from https://www.python.org/downloads/windows/\nand tick \"Add python.exe to PATH\". Then run Nova Setup again.\n\nThis has not yet been tested on a real Windows 11 PC.")
			os.Exit(1)
		}
		info("Nova Setup", "Nova will now create a local virtual environment and install its UI toolkit.\nA command window will open. This can take a few minutes.\nModels are downloaded in the Setup window afterwards — they are not bundled here.")
		args := append(append([]string{}, extra...), "-m", "venv", ".venv")
		if err := run(root, py, args, true); err != nil {
			message("Nova Setup", "Could not create a virtual environment.\nMake sure Python 3.10+ is installed and 'Add to PATH' was ticked.\n\n"+err.Error())
			os.Exit(1)
		}
		pip := []string{venvPy, "-m", "pip", "install", "--upgrade", "pip"}
		_ = run(root, pip[0], pip[1:], true)
		req := filepath.Join(root, "requirements.txt")
		if err := run(root, venvPy, []string{"-m", "pip", "install", "-r", req}, true); err != nil {
			message("Nova Setup", "Could not install PySide6 into the local environment.\nCheck the network and try Nova Setup again.\n\n"+err.Error())
			os.Exit(1)
		}
	}

	py := venvPyw
	if !exists(py) || setup {
		py = venvPy
	}
	args := []string{"-m", "nova"}
	if setup {
		args = append(args, "--setup")
	}
	// Extra args after our own.
	for _, a := range os.Args[1:] {
		if a != "--setup" {
			args = append(args, a)
		}
	}
	if err := start(root, py, args); err != nil {
		message("Nova", "Could not start Nova.\n\n"+err.Error())
		os.Exit(1)
	}
}
