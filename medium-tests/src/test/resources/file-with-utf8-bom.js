<label className={/*multiple classes*/}>
  <span className={/*single class*/}>
     Text <span className={/*single class*/}>(suffix)</span>
  </span>
    <span className={/*single class*/}>
    <input
        ref={checkbox}
        type="checkbox"
        name={name}
        value={value}
        tabIndex={-1}
        className={/*single class*/}
        checked={checked}
        disabled={disabled}
        onChange={(evt) => onChange(evt)}
    ></input>
    <button
        className={/*multiple classes*/}
        disabled={disabled}
        onClick={(evt) => onClick(evt)}
    ></button>
</span>
</label>
