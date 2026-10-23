var Person = function(first, last, middle) {
    this.first = first;
    this.middle = middle; this.last = last;
};

Person.prototype = {

    whoAreYou : function() {
        fullName = [this.first, this.middle, this.last].filter(x => x).join(' ');
        return fullName;
    }

};
