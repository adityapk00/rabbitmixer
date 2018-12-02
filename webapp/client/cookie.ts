class Cookie {

    public read(name: string) {
        var result = new RegExp('(?:^|; )' + encodeURIComponent(name) + '=([^;]*)').exec(document.cookie);
        return result ? result[1] : null;
    }
    
    public write(name: string, value: string, days?: number) {
        if (!days) {
            days = 365 * 20;
        }
        
        var date = new Date();
        date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
        
        var expires = "; expires=" + date.toUTCString();
        
        document.cookie = name + "=" + value + expires + "; path=/";
    }
    
    public remove(name: string) {
        this.write(name, "", -1);
    }	
}

export var cookie: Cookie = new Cookie();