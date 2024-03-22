export default function WordWithStress(params) {
    const text = params.text
    const stressIndex = params.stressIndex
    const stressLength = params.stressLength
    if (stressIndex != null) {
        return <>
            {params.reconstructed === true && "*"}
            {text.substring(0, stressIndex)}
            <span className="stressed">{text.substring(stressIndex, stressIndex+stressLength)}</span>
            {text.substring(stressIndex+stressLength)}
        </>
    }
    return params.reconstructed === true ? "*" + text : text
}

