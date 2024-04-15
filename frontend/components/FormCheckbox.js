import {useFormContext} from "react-hook-form";

export default function FormCheckbox(props) {
    const {register} = useFormContext()
    return <>
        <input type="checkbox" {...register(props.id)}/>
        {props.label}
        <br/></>
}
