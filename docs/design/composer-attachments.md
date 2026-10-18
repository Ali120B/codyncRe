# Composer attachments

iPhone and the desktop app show local image attachments as thumbnails above the text field,
with a remove button in the upper-right corner. The entire image fits inside the
preview; its original data remains the attachment sent to the host. On iPhone,
ImageIO creates display-sized thumbnails off the main actor; the desktop app
(`views/thread/ComposerAttachment.tsx`) lets the browser decode the image. Invalid image data falls
back to the filename chip, as do non-image files. Multiple attachments scroll
horizontally and remain removable individually.

On the iPhone a copied image goes in through **Paste** in the + menu; the
composer never suggests the clipboard on its own, and focusing it does not read
or attach clipboard contents. Picking a
photo or image file uses the same preview. A draft containing only attachments
shows Send, including when another reply is running (the message is queued).

The TUI retains its filename row and backspace removal: its text renderer does
not implement terminal image protocols.
No upload API, message format, or attachment limit changes.
