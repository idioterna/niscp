# niscp - Secure Image Upload via SSH

_vibe coded - i didn't do anything, i merely held a glorified markov chain's hand through the process_

niscp is an Android application that provides secure image upload functionality using SSH/SFTP. It allows users to upload images from their device to a remote server without relying on cloud services, ensuring privacy and data control.

## Features

- **Secure SSH Upload**: Uses JSch library (GPL licensed) for secure file transfers
- **Background Service**: Continuous upload service that processes images in the background
- **Image Processing**: Optional image resizing with configurable width
- **SSH Key Management**: Generate SSH key pairs and copy public keys to clipboard
- **File Sharing Integration**: Register as a destination for sharing images from other apps
- **Modern UI**: Material Design 3 interface with intuitive controls
- **Configuration Panel**: Easy setup for SSH connection parameters

## Setup Instructions

### 1. Server Configuration

1. **SSH Server**: Ensure your remote server has SSH/SFTP enabled
2. **User Account**: Create a user account on the server for the app
3. **Directory Permissions**: Ensure the user has write permissions to the target directory

### 2. App Configuration

1. **Open the App**: Launch niscp on your Android device
2. **Configure Settings**: Enter the following information:
   - **Hostname**: Your server's IP address or domain name
   - **Port**: SSH port (default: 22)
   - **Username**: Your server username
   - **Remote Directory**: Target directory on the server (e.g., `/home/user/images`)
   - **URL Prefix**: Web server URL where images will be publicly accessible (e.g., `https://example.com/user/images/`)
3. **Image Size Settings**:
   - Choose "Original Size" to upload images without modification
   - Choose "Custom Width" to resize images to a specific width in pixels
4. **Generate SSH Key**: Tap "Generate SSH Key Pair" to create a new Ed25519 key pair (using BouncyCastle)
5. **Copy Public Key**: Tap "Copy Public Key to Clipboard" and add it to your server's `~/.ssh/authorized_keys`
6. **Test Connection**: Tap "Test Connection" to verify your SSH setup
7. **Start Service**: Tap "Start Service" to begin the background upload service

### 3. Server SSH Key Setup

1. Copy the public key from the app (it's automatically copied to clipboard)
2. On your server, add the key to the user's authorized keys:
   ```bash
   mkdir -p ~/.ssh
   echo "PASTE_PUBLIC_KEY_HERE" >> ~/.ssh/authorized_keys
   chmod 700 ~/.ssh
   chmod 600 ~/.ssh/authorized_keys
   ```

### 4. Enhanced Server Security (Optional)

For enhanced security, you can configure your server to use Ed25519 host keys:

1. **Generate Ed25519 host key on server**:
   ```bash
   sudo ssh-keygen -t ed25519 -f /etc/ssh/ssh_host_ed25519_key
   ```

2. **Update SSH server configuration** (`/etc/ssh/sshd_config`):
   ```
   HostKey /etc/ssh/ssh_host_ed25519_key
   ```

3. **Restart SSH service**:
   ```bash
   sudo systemctl restart sshd
   ```

The app is configured to support Ed25519 host keys and will automatically use them when available.

## Usage

### Starting the Upload Service

1. **Start Service**: Tap "Start Service" in the main app to begin the background upload service
2. **Service Status**: The app shows whether the service is running or stopped
3. **Background Operation**: The service runs in the background and processes queued images

### Uploading Images

#### Method 1: Share from Other Apps
1. Open any app that can share images (Gallery, Camera, etc.)
2. Select one or multiple images and tap the share button
3. Choose "niscp" from the share options
4. Optionally enter a custom filename prefix (e.g., "vacation", "party")
5. Tap "Upload Images" to queue them for upload

#### Method 2: Multiple Image Support
- **Single Image**: Share one image for immediate processing
- **Multiple Images**: Share multiple images at once for batch processing
- **Custom Naming**: Enter a prefix to rename files (e.g., "vacation-001.jpg", "vacation-002.jpg")
- **Original Filenames**: When no prefix is entered, original device filenames are preserved
- **Automatic Numbering**: Images are automatically numbered with zero-padded integers

#### Method 3: Direct Upload
1. Images shared to niscp are queued for upload
2. The background service processes the queue and uploads images via SSH
3. Upload progress is shown in the notification area

#### Method 4: URL Sharing
1. After uploading images, URLs are constructed using the configured public URL prefix
2. URLs are copied as newline-separated text for easy sharing in messages
3. Example URLs: `https://example.com/user/images/vacation-001.jpg`

### Image Processing

- **Original Size**: Images are uploaded without modification
- **Custom Width**: Images are resized to the specified width while maintaining aspect ratio
- **EXIF Data**: Important metadata (date, GPS, camera info) is preserved when possible
- **Quality**: Images are compressed with 90% JPEG quality for optimal file size
- **Batch Processing**: Multiple images can be processed and uploaded simultaneously
- **Custom Naming**: Files can be renamed with custom prefixes and automatic numbering
- **Original Filenames**: Preserves original device filenames when no custom prefix is provided
- **URL Generation**: Automatically constructs public URLs for uploaded images

## Technical Details

### Architecture

- **MainActivity**: Main interface for service control and settings
- **ShareReceiverActivity**: Handles incoming single and multiple image shares with custom naming interface
- **ImageUploadService**: Background service for processing and uploading images with custom naming
- **SSHKeyManager**: Manages SSH key pair generation and storage
- **ImageProcessor**: Handles image resizing and processing
- **SettingsRepository**: Manages app configuration using DataStore with automatic updates

### Dependencies

- **Apache SSHD**: Modern SSH library with Ed25519 support for secure file transfers
- **BouncyCastle**: Cryptographic library for Ed25519 key generation and parsing
- **AndroidX**: Modern Android development libraries
- **Material Design**: UI components and theming
- **Coroutines**: Asynchronous programming
- **DataStore**: Modern preferences storage

### Security Features

- **SSH Key Authentication**: Uses Ed25519 key pairs for secure authentication
- **Ed25519 Host Key Support**: Supports Ed25519 server host keys for enhanced security
- **No Cloud Dependencies**: All data stays on your server (represented by crossed-out cloud icon)
- **Local Key Storage**: SSH keys are stored securely on the device
- **Encrypted Transfers**: All file transfers are encrypted via SSH

## Permissions

The app requires the following permissions:

- **Internet**: For SSH connections to remote servers
- **Network State**: To check network connectivity
- **Storage**: To read and process images
- **Foreground Service**: To run the upload service in the background
- **Notifications**: To show upload status

## Troubleshooting

### Connection Issues

1. **Check Hostname**: Ensure the hostname is correct and resolvable
2. **Verify Port**: Confirm the SSH port is correct (usually 22)
3. **Network Access**: Ensure the device can reach the server
4. **SSH Key**: Verify the public key is properly added to the server

### Upload Issues

1. **Directory Permissions**: Check that the remote directory is writable
2. **Disk Space**: Ensure the server has sufficient storage space
3. **Service Status**: Verify the upload service is running
4. **Image Format**: Ensure images are in supported formats (JPEG, PNG, etc.)

### Performance

1. **Image Size**: Large images may take longer to process and upload
2. **Network Speed**: Upload speed depends on your internet connection
3. **Server Performance**: Server response time affects upload speed

## License

This project uses the JSch library which is licensed under the GPL. Please ensure compliance with the GPL license terms.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## Support

For issues and questions, please create an issue in the project repository.
